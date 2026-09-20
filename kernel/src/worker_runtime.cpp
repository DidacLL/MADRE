#include "worker_runtime.hpp"

#include <algorithm>
#include <cerrno>
#include <chrono>
#include <csignal>
#include <cstring>
#include <cstdlib>
#include <fcntl.h>
#include <future>
#include <spawn.h>
#include <stdexcept>
#include <string>
#include <sys/types.h>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>

extern char** environ;

namespace madre::kernel {
namespace {
using namespace std::chrono_literals;

std::int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::system_clock::now().time_since_epoch())
        .count();
}

void close_fd(int& fd) noexcept {
    if (fd >= 0) {
        ::close(fd);
        fd = -1;
    }
}

void set_cloexec(int fd) {
    const int flags = ::fcntl(fd, F_GETFD);
    if (flags < 0 || ::fcntl(fd, F_SETFD, flags | FD_CLOEXEC) < 0) {
        throw std::runtime_error("set worker pipe close-on-exec failed: " +
                                 std::string(std::strerror(errno)));
    }
}

void make_cloexec_pipe(int (&fds)[2]) {
    if (::pipe(fds) != 0) {
        throw std::runtime_error("create worker pipe failed: " + std::string(std::strerror(errno)));
    }
    try {
        set_cloexec(fds[0]);
        set_cloexec(fds[1]);
    } catch (...) {
        ::close(fds[0]);
        ::close(fds[1]);
        fds[0] = -1;
        fds[1] = -1;
        throw;
    }
}

void validate_requirement(const ResourceRequirement& requirement) {
    if (requirement.cpu_slots < 1 || requirement.ram_bytes == 0) {
        throw std::invalid_argument("engine resource requirement must provide positive CPU and RAM");
    }
    if (requirement.gpu_id.empty() != (requirement.gpu_vram_bytes == 0)) {
        throw std::invalid_argument("GPU identity and VRAM requirement must be declared together");
    }
}

std::string wait_status_description(int status) {
    if (WIFEXITED(status)) {
        return "exit code " + std::to_string(WEXITSTATUS(status));
    }
    if (WIFSIGNALED(status)) {
        return "signal " + std::to_string(WTERMSIG(status));
    }
    return "unknown process termination";
}

}  // namespace

ResourceManager::Lease::Lease(ResourceManager* owner, ResourceRequirement requirement)
    : owner_(owner), requirement_(std::move(requirement)) {}

ResourceManager::Lease::Lease(Lease&& other) noexcept
    : owner_(other.owner_), requirement_(std::move(other.requirement_)) {
    other.owner_ = nullptr;
}

ResourceManager::Lease& ResourceManager::Lease::operator=(Lease&& other) noexcept {
    if (this != &other) {
        reset();
        owner_ = other.owner_;
        requirement_ = std::move(other.requirement_);
        other.owner_ = nullptr;
    }
    return *this;
}

ResourceManager::Lease::~Lease() {
    reset();
}

void ResourceManager::Lease::reset() noexcept {
    if (owner_ != nullptr) {
        owner_->release(requirement_);
        owner_ = nullptr;
    }
}

ResourceManager::ResourceManager(ResourceCapacity capacity) : capacity_(std::move(capacity)) {
    if (capacity_.cpu_slots < 1 || capacity_.ram_bytes == 0) {
        throw std::invalid_argument("resource capacity must provide positive CPU and RAM");
    }
}

bool ResourceManager::can_ever_reserve(const ResourceRequirement& requirement) {
    validate_requirement(requirement);
    std::lock_guard lock(mutex_);
    if (requirement.cpu_slots > capacity_.cpu_slots ||
        requirement.ram_bytes > capacity_.ram_bytes) {
        return false;
    }
    if (requirement.gpu_id.empty()) {
        return true;
    }
    const auto capacity = capacity_.gpu_vram_bytes.find(requirement.gpu_id);
    return capacity != capacity_.gpu_vram_bytes.end() &&
           requirement.gpu_vram_bytes <= capacity->second;
}

std::optional<ResourceManager::Lease> ResourceManager::try_reserve(
    const ResourceRequirement& requirement) {
    validate_requirement(requirement);

    std::lock_guard lock(mutex_);
    if (requirement.cpu_slots > capacity_.cpu_slots - cpu_used_ ||
        requirement.ram_bytes > capacity_.ram_bytes - ram_used_) {
        return std::nullopt;
    }

    if (!requirement.gpu_id.empty()) {
        const auto capacity = capacity_.gpu_vram_bytes.find(requirement.gpu_id);
        if (capacity == capacity_.gpu_vram_bytes.end()) {
            return std::nullopt;
        }
        const auto used = gpu_vram_used_[requirement.gpu_id];
        if (requirement.gpu_vram_bytes > capacity->second - used) {
            return std::nullopt;
        }
    }

    cpu_used_ += requirement.cpu_slots;
    ram_used_ += requirement.ram_bytes;
    if (!requirement.gpu_id.empty()) {
        gpu_vram_used_[requirement.gpu_id] += requirement.gpu_vram_bytes;
    }
    return Lease(this, requirement);
}

void ResourceManager::release(const ResourceRequirement& requirement) noexcept {
    std::lock_guard lock(mutex_);
    cpu_used_ -= requirement.cpu_slots;
    ram_used_ -= requirement.ram_bytes;
    if (!requirement.gpu_id.empty()) {
        auto it = gpu_vram_used_.find(requirement.gpu_id);
        if (it != gpu_vram_used_.end()) {
            it->second -= requirement.gpu_vram_bytes;
            if (it->second == 0) {
                gpu_vram_used_.erase(it);
            }
        }
    }
}

struct WorkerPool::WorkerProcess {
    WorkerProcess(std::filesystem::path executable, std::string engine_id, int fake_delay_ms)
        : engine_id(std::move(engine_id)) {
        int parent_to_child[2]{-1, -1};
        int child_to_parent[2]{-1, -1};
        try {
            make_cloexec_pipe(parent_to_child);
            make_cloexec_pipe(child_to_parent);
        } catch (...) {
            if (parent_to_child[0] >= 0) {
                ::close(parent_to_child[0]);
                ::close(parent_to_child[1]);
            }
            throw;
        }

        posix_spawn_file_actions_t actions{};
        int rc = ::posix_spawn_file_actions_init(&actions);
        if (rc != 0) {
            ::close(parent_to_child[0]);
            ::close(parent_to_child[1]);
            ::close(child_to_parent[0]);
            ::close(child_to_parent[1]);
            throw std::runtime_error("initialize worker spawn actions failed: " +
                                     std::string(std::strerror(rc)));
        }

        const auto add_action = [&](int action_rc, const char* description) {
            if (action_rc != 0) {
                ::posix_spawn_file_actions_destroy(&actions);
                ::close(parent_to_child[0]);
                ::close(parent_to_child[1]);
                ::close(child_to_parent[0]);
                ::close(child_to_parent[1]);
                throw std::runtime_error(std::string(description) + ": " +
                                         std::strerror(action_rc));
            }
        };

        add_action(::posix_spawn_file_actions_adddup2(
                       &actions, parent_to_child[0], STDIN_FILENO),
                   "configure worker stdin");
        add_action(::posix_spawn_file_actions_adddup2(
                       &actions, child_to_parent[1], STDOUT_FILENO),
                   "configure worker stdout");
        add_action(::posix_spawn_file_actions_addclose(&actions, parent_to_child[0]),
                   "close worker inherited stdin pipe");
        add_action(::posix_spawn_file_actions_addclose(&actions, parent_to_child[1]),
                   "close worker inherited parent input pipe");
        add_action(::posix_spawn_file_actions_addclose(&actions, child_to_parent[0]),
                   "close worker inherited parent output pipe");
        add_action(::posix_spawn_file_actions_addclose(&actions, child_to_parent[1]),
                   "close worker inherited stdout pipe");

        const auto executable_string = executable.string();
        const auto delay = std::to_string(fake_delay_ms);
        char* argv[] = {
            const_cast<char*>(executable_string.c_str()),
            const_cast<char*>("--engine-id"),
            this->engine_id.data(),
            const_cast<char*>("--delay-ms"),
            const_cast<char*>(delay.c_str()),
            nullptr,
        };

        pid_t child = -1;
        rc = ::posix_spawn(
            &child, executable_string.c_str(), &actions, nullptr, argv, environ);
        ::posix_spawn_file_actions_destroy(&actions);
        if (rc != 0) {
            ::close(parent_to_child[0]);
            ::close(parent_to_child[1]);
            ::close(child_to_parent[0]);
            ::close(child_to_parent[1]);
            throw std::runtime_error("spawn worker failed: " + std::string(std::strerror(rc)));
        }

        pid = child;
        ::close(parent_to_child[0]);
        ::close(child_to_parent[1]);
        input_fd = parent_to_child[1];
        output_fd = child_to_parent[0];
        last_used_ms = now_ms();
    }

    ~WorkerProcess() {
        terminate();
    }

    bool alive() {
        if (pid < 0) {
            return false;
        }
        int status = 0;
        const auto result = ::waitpid(pid, &status, WNOHANG);
        if (result == 0) {
            return true;
        }
        if (result == pid) {
            last_wait_status = status;
            pid = -1;
            close_fd(input_fd);
            close_fd(output_fd);
            return false;
        }
        if (result < 0 && errno == ECHILD) {
            pid = -1;
            close_fd(input_fd);
            close_fd(output_fd);
            return false;
        }
        return true;
    }

    std::string exit_description() const {
        if (last_wait_status) {
            return wait_status_description(*last_wait_status);
        }
        return "worker process exited";
    }

    void terminate() noexcept {
        if (pid >= 0) {
            ::kill(pid, SIGKILL);
            int status = 0;
            while (::waitpid(pid, &status, 0) < 0 && errno == EINTR) {
            }
            last_wait_status = status;
            pid = -1;
        }
        close_fd(input_fd);
        close_fd(output_fd);
    }

    WorkerOutcome execute(
        const std::vector<std::uint8_t>& payload,
        std::uint64_t correlation_id,
        std::int64_t attempt_started_at_ms,
        const std::function<bool()>& cancellation_requested,
        const std::function<bool()>& kernel_stopping,
        std::optional<std::int64_t> stop_at_ms,
        bool timeout_wins) {
        if (!alive()) {
            return {WorkerOutcomeKind::Crashed, {}, "WORKER_CRASH: " + exit_description()};
        }

        try {
            Frame request{MessageType::WorkerExecute, correlation_id,
                          {{"engine_id", engine_id},
                           {"attempt_started_at_ms", std::to_string(attempt_started_at_ms)}},
                          payload};
            write_frame(input_fd, request);
        } catch (const std::exception& ex) {
            if (!alive()) {
                return {WorkerOutcomeKind::Crashed, {}, "WORKER_CRASH: " + exit_description()};
            }
            terminate();
            return {WorkerOutcomeKind::Crashed, {}, std::string("WORKER_IPC_FAILURE: ") + ex.what()};
        }

        auto response_future = std::async(std::launch::async, [fd = output_fd] {
            return read_frame(fd);
        });

        const auto terminate_and_drain = [&] {
            terminate();
            try {
                (void)response_future.get();
            } catch (...) {
            }
        };

        while (response_future.wait_for(10ms) != std::future_status::ready) {
            if (kernel_stopping()) {
                terminate_and_drain();
                return {WorkerOutcomeKind::Stopped, {}, {}};
            }
            if (cancellation_requested()) {
                terminate_and_drain();
                return {WorkerOutcomeKind::Cancelled, {}, "cancelled"};
            }
            if (stop_at_ms && now_ms() >= *stop_at_ms) {
                terminate_and_drain();
                const std::string failure = timeout_wins
                    ? "ATTEMPT_TIMEOUT: per-attempt timeout expired"
                    : "DEADLINE_EXPIRED: Work deadline expired during attempt";
                return {WorkerOutcomeKind::TimedOut, {}, failure};
            }
            if (!alive()) {
                try {
                    (void)response_future.get();
                } catch (...) {
                }
                return {WorkerOutcomeKind::Crashed, {}, "WORKER_CRASH: " + exit_description()};
            }
        }

        try {
            const auto response = response_future.get();
            if (response.correlation_id != correlation_id) {
                terminate();
                return {WorkerOutcomeKind::TechnicalFailure, {},
                        "WORKER_PROTOCOL_FAILURE: correlation id mismatch"};
            }
            if (response.type == MessageType::WorkerResult) {
                return {WorkerOutcomeKind::Succeeded, response.payload, {}};
            }
            if (response.type == MessageType::WorkerFailure) {
                const auto it = response.metadata.find("technical_failure");
                return {WorkerOutcomeKind::TechnicalFailure, {},
                        it == response.metadata.end()
                            ? "WORKER_PROTOCOL_FAILURE: missing technical failure"
                            : it->second};
            }
            terminate();
            return {WorkerOutcomeKind::TechnicalFailure, {},
                    "WORKER_PROTOCOL_FAILURE: unexpected worker response"};
        } catch (const std::exception& ex) {
            if (!alive()) {
                return {WorkerOutcomeKind::Crashed, {}, "WORKER_CRASH: " + exit_description()};
            }
            terminate();
            return {WorkerOutcomeKind::TechnicalFailure, {},
                    std::string("WORKER_PROTOCOL_FAILURE: ") + ex.what()};
        }
    }

    std::string engine_id;
    pid_t pid{-1};
    int input_fd{-1};
    int output_fd{-1};
    bool busy{};
    std::int64_t last_used_ms{};
    std::optional<int> last_wait_status;
};

WorkerPool::Lease::Lease(WorkerPool* pool, std::shared_ptr<WorkerProcess> worker)
    : pool_(pool), worker_(std::move(worker)) {}

WorkerPool::Lease::Lease(Lease&& other) noexcept
    : pool_(other.pool_), worker_(std::move(other.worker_)) {
    other.pool_ = nullptr;
}

WorkerPool::Lease& WorkerPool::Lease::operator=(Lease&& other) noexcept {
    if (this != &other) {
        reset();
        pool_ = other.pool_;
        worker_ = std::move(other.worker_);
        other.pool_ = nullptr;
    }
    return *this;
}

WorkerPool::Lease::~Lease() {
    reset();
}

void WorkerPool::Lease::reset() noexcept {
    if (pool_ != nullptr && worker_) {
        pool_->release(worker_);
    }
    pool_ = nullptr;
    worker_.reset();
}

WorkerOutcome WorkerPool::Lease::execute(
    const std::vector<std::uint8_t>& payload,
    std::uint64_t correlation_id,
    std::int64_t attempt_started_at_ms,
    const std::function<bool()>& cancellation_requested,
    const std::function<bool()>& kernel_stopping,
    std::optional<std::int64_t> stop_at_ms,
    bool timeout_wins) {
    if (!worker_) {
        throw std::runtime_error("worker lease is empty");
    }
    return worker_->execute(payload, correlation_id, attempt_started_at_ms, cancellation_requested,
                            kernel_stopping, stop_at_ms, timeout_wins);
}

int WorkerPool::Lease::pid() const {
    return worker_ ? static_cast<int>(worker_->pid) : -1;
}

WorkerPool::WorkerPool(
    std::filesystem::path executable, int fake_delay_ms, std::int64_t idle_timeout_ms)
    : executable_(std::move(executable)),
      fake_delay_ms_(fake_delay_ms),
      idle_timeout_ms_(idle_timeout_ms) {
    std::signal(SIGPIPE, SIG_IGN);
    if (fake_delay_ms_ < 0 || idle_timeout_ms_ < 0) {
        throw std::invalid_argument("worker delay and idle timeout must be nonnegative");
    }
}

WorkerPool::~WorkerPool() {
    shutdown();
}

WorkerPool::Lease WorkerPool::acquire(const std::string& engine_id) {
    std::lock_guard lock(mutex_);
    for (auto it = workers_.begin(); it != workers_.end();) {
        auto& worker = *it;
        if (!worker->busy && !worker->alive()) {
            it = workers_.erase(it);
            continue;
        }
        if (!worker->busy && worker->engine_id == engine_id) {
            worker->busy = true;
            return Lease(this, worker);
        }
        ++it;
    }

    auto worker = std::make_shared<WorkerProcess>(executable_, engine_id, fake_delay_ms_);
    worker->busy = true;
    workers_.push_back(worker);
    return Lease(this, std::move(worker));
}

bool WorkerPool::is_warm(std::string_view engine_id) {
    std::lock_guard lock(mutex_);
    for (auto it = workers_.begin(); it != workers_.end();) {
        if (!(*it)->alive()) {
            it = workers_.erase(it);
            continue;
        }
        if ((*it)->engine_id == engine_id) {
            return true;
        }
        ++it;
    }
    return false;
}

void WorkerPool::reap_idle(std::int64_t current_ms) {
    std::lock_guard lock(mutex_);
    for (auto it = workers_.begin(); it != workers_.end();) {
        auto& worker = *it;
        if (!worker->alive()) {
            it = workers_.erase(it);
            continue;
        }
        if (!worker->busy && current_ms - worker->last_used_ms >= idle_timeout_ms_) {
            worker->terminate();
            it = workers_.erase(it);
            continue;
        }
        ++it;
    }
}

void WorkerPool::shutdown() noexcept {
    std::lock_guard lock(mutex_);
    for (auto& worker : workers_) {
        worker->terminate();
    }
    workers_.clear();
}

void WorkerPool::release(const std::shared_ptr<WorkerProcess>& worker) noexcept {
    std::lock_guard lock(mutex_);
    const auto it = std::find(workers_.begin(), workers_.end(), worker);
    if (it == workers_.end()) {
        return;
    }
    worker->busy = false;
    worker->last_used_ms = now_ms();
    if (!worker->alive()) {
        workers_.erase(it);
    }
}

}  // namespace madre::kernel
