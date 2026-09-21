#include "worker_runtime.hpp"

#include <algorithm>
#include <cerrno>
#include <chrono>
#include <climits>
#include <csignal>
#include <cstring>
#include <cstdlib>
#include <stdexcept>
#include <string>
#include <thread>

#ifdef _WIN32
#include <fcntl.h>
#include <io.h>
#include <windows.h>
#else
#include <fcntl.h>
#include <poll.h>
#include <spawn.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

extern char** environ;
#endif

namespace madre::kernel {
namespace {
using namespace std::chrono_literals;

std::int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::system_clock::now().time_since_epoch())
        .count();
}

void close_fd(int& fd) noexcept {
    if (fd < 0) return;
#ifdef _WIN32
    ::_close(fd);
#else
    ::close(fd);
#endif
    fd = -1;
}

#ifndef _WIN32
void set_cloexec(int fd) {
    const int flags = ::fcntl(fd, F_GETFD);
    if (flags < 0 || ::fcntl(fd, F_SETFD, flags | FD_CLOEXEC) < 0) {
        throw std::runtime_error(
            "set worker pipe close-on-exec failed: " +
            std::string(std::strerror(errno)));
    }
}

void normalize_pipe_fd(int& fd) {
    if (fd > STDERR_FILENO) {
        set_cloexec(fd);
        return;
    }
    const int duplicate =
        ::fcntl(fd, F_DUPFD_CLOEXEC, STDERR_FILENO + 1);
    if (duplicate < 0) {
        throw std::runtime_error(
            "move worker pipe above standard descriptors failed: " +
            std::string(std::strerror(errno)));
    }
    ::close(fd);
    fd = duplicate;
}

void set_nonblocking(int fd) {
    const int flags = ::fcntl(fd, F_GETFL);
    if (flags < 0 ||
        ::fcntl(fd, F_SETFL, flags | O_NONBLOCK) < 0) {
        throw std::runtime_error(
            "set worker pipe nonblocking failed: " +
            std::string(std::strerror(errno)));
    }
}

void make_cloexec_pipe(int (&fds)[2]) {
    if (::pipe(fds) != 0) {
        throw std::runtime_error(
            "create worker pipe failed: " +
            std::string(std::strerror(errno)));
    }
    try {
        normalize_pipe_fd(fds[0]);
        normalize_pipe_fd(fds[1]);
    } catch (...) {
        close_fd(fds[0]);
        close_fd(fds[1]);
        throw;
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
#else
std::wstring utf8_to_wide(const std::string& value) {
    if (value.empty()) return {};
    const int count = ::MultiByteToWideChar(
        CP_UTF8, MB_ERR_INVALID_CHARS, value.data(),
        static_cast<int>(value.size()), nullptr, 0);
    if (count <= 0) {
        throw std::runtime_error(
            "worker argument is not valid UTF-8: Windows error " +
            std::to_string(::GetLastError()));
    }
    std::wstring result(static_cast<std::size_t>(count), L'\0');
    if (::MultiByteToWideChar(
            CP_UTF8, MB_ERR_INVALID_CHARS, value.data(),
            static_cast<int>(value.size()), result.data(), count) != count) {
        throw std::runtime_error(
            "convert worker argument to Windows UTF-16 failed");
    }
    return result;
}

std::wstring quote_windows_argument(const std::wstring& value) {
    if (value.empty()) return L"\"\"";
    if (value.find_first_of(L" \t\"") == std::wstring::npos) {
        return value;
    }
    std::wstring result = L"\"";
    std::size_t slashes = 0;
    for (wchar_t ch : value) {
        if (ch == L'\\') {
            ++slashes;
            continue;
        }
        if (ch == L'\"') {
            result.append(slashes * 2 + 1, L'\\');
            result.push_back(L'\"');
            slashes = 0;
            continue;
        }
        result.append(slashes, L'\\');
        slashes = 0;
        result.push_back(ch);
    }
    result.append(slashes * 2, L'\\');
    result.push_back(L'\"');
    return result;
}

std::wstring worker_command_line(const WorkerLaunchSpec& launch) {
    std::wstring result =
        quote_windows_argument(launch.executable.wstring());
    for (const auto& argument : launch.arguments) {
        result.push_back(L' ');
        result += quote_windows_argument(utf8_to_wide(argument));
    }
    return result;
}

HANDLE inheritable_stderr(const SECURITY_ATTRIBUTES& attributes) {
    HANDLE current = ::GetStdHandle(STD_ERROR_HANDLE);
    if (current != nullptr && current != INVALID_HANDLE_VALUE) {
        HANDLE duplicate = nullptr;
        if (::DuplicateHandle(
                ::GetCurrentProcess(), current,
                ::GetCurrentProcess(), &duplicate,
                0, TRUE, DUPLICATE_SAME_ACCESS)) {
            return duplicate;
        }
    }
    HANDLE null_handle = ::CreateFileW(
        L"NUL", GENERIC_WRITE,
        FILE_SHARE_READ | FILE_SHARE_WRITE,
        const_cast<SECURITY_ATTRIBUTES*>(&attributes),
        OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (null_handle == INVALID_HANDLE_VALUE) {
        throw std::runtime_error(
            "open worker fallback stderr failed: Windows error " +
            std::to_string(::GetLastError()));
    }
    return null_handle;
}
#endif

void validate_requirement(
    const ResourceRequirement& requirement) {
    if (requirement.cpu_slots < 1 || requirement.ram_bytes == 0) {
        throw std::invalid_argument(
            "engine resource requirement must provide positive CPU and RAM");
    }
    if (requirement.gpu_id.empty() !=
        (requirement.gpu_vram_bytes == 0)) {
        throw std::invalid_argument(
            "GPU identity and VRAM requirement must be declared together");
    }
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
    WorkerProcess(
        const WorkerLaunchSpec& launch,
        std::string engine_id_value)
        : engine_id(std::move(engine_id_value)) {
#ifdef _WIN32
        SECURITY_ATTRIBUTES attributes{};
        attributes.nLength = sizeof(attributes);
        attributes.bInheritHandle = TRUE;

        HANDLE child_input = nullptr;
        HANDLE parent_input = nullptr;
        HANDLE parent_output = nullptr;
        HANDLE child_output = nullptr;
        if (!::CreatePipe(
                &child_input, &parent_input, &attributes,
                2U * 1024U * 1024U) ||
            !::CreatePipe(
                &parent_output, &child_output, &attributes,
                2U * 1024U * 1024U)) {
            const auto error = ::GetLastError();
            if (child_input) ::CloseHandle(child_input);
            if (parent_input) ::CloseHandle(parent_input);
            if (parent_output) ::CloseHandle(parent_output);
            if (child_output) ::CloseHandle(child_output);
            throw std::runtime_error(
                "create worker pipe failed: Windows error " +
                std::to_string(error));
        }
        if (!::SetHandleInformation(
                parent_input, HANDLE_FLAG_INHERIT, 0) ||
            !::SetHandleInformation(
                parent_output, HANDLE_FLAG_INHERIT, 0)) {
            const auto error = ::GetLastError();
            ::CloseHandle(child_input);
            ::CloseHandle(parent_input);
            ::CloseHandle(parent_output);
            ::CloseHandle(child_output);
            throw std::runtime_error(
                "make parent worker pipe private failed: Windows error " +
                std::to_string(error));
        }

        HANDLE child_error = INVALID_HANDLE_VALUE;
        try {
            child_error = inheritable_stderr(attributes);
        } catch (...) {
            ::CloseHandle(child_input);
            ::CloseHandle(parent_input);
            ::CloseHandle(parent_output);
            ::CloseHandle(child_output);
            throw;
        }

        STARTUPINFOW startup{};
        startup.cb = sizeof(startup);
        startup.dwFlags = STARTF_USESTDHANDLES;
        startup.hStdInput = child_input;
        startup.hStdOutput = child_output;
        startup.hStdError = child_error;
        PROCESS_INFORMATION process{};
        auto command = worker_command_line(launch);
        const BOOL created = ::CreateProcessW(
            launch.executable.c_str(), command.data(),
            nullptr, nullptr, TRUE, CREATE_NO_WINDOW,
            nullptr, nullptr, &startup, &process);
        const auto create_error =
            created ? ERROR_SUCCESS : ::GetLastError();

        ::CloseHandle(child_input);
        ::CloseHandle(child_output);
        ::CloseHandle(child_error);
        if (!created) {
            ::CloseHandle(parent_input);
            ::CloseHandle(parent_output);
            throw std::runtime_error(
                "spawn worker failed: Windows error " +
                std::to_string(create_error));
        }

        process_handle = process.hProcess;
        process_id = process.dwProcessId;
        ::CloseHandle(process.hThread);

        input_fd = ::_open_osfhandle(
            reinterpret_cast<std::intptr_t>(parent_input), _O_BINARY);
        if (input_fd < 0) {
            ::CloseHandle(parent_input);
            ::CloseHandle(parent_output);
            terminate();
            throw std::runtime_error(
                "convert worker input pipe to descriptor failed");
        }
        output_fd = ::_open_osfhandle(
            reinterpret_cast<std::intptr_t>(parent_output), _O_BINARY);
        if (output_fd < 0) {
            ::CloseHandle(parent_output);
            terminate();
            throw std::runtime_error(
                "convert worker output pipe to descriptor failed");
        }
#else
        int parent_to_child[2]{-1, -1};
        int child_to_parent[2]{-1, -1};
        try {
            make_cloexec_pipe(parent_to_child);
            make_cloexec_pipe(child_to_parent);
        } catch (...) {
            close_fd(parent_to_child[0]);
            close_fd(parent_to_child[1]);
            close_fd(child_to_parent[0]);
            close_fd(child_to_parent[1]);
            throw;
        }

        posix_spawn_file_actions_t actions{};
        int rc = ::posix_spawn_file_actions_init(&actions);
        if (rc != 0) {
            close_fd(parent_to_child[0]);
            close_fd(parent_to_child[1]);
            close_fd(child_to_parent[0]);
            close_fd(child_to_parent[1]);
            throw std::runtime_error(
                "initialize worker spawn actions failed: " +
                std::string(std::strerror(rc)));
        }
        const auto add_action =
            [&](int action_rc, const char* description) {
                if (action_rc != 0) {
                    ::posix_spawn_file_actions_destroy(&actions);
                    close_fd(parent_to_child[0]);
                    close_fd(parent_to_child[1]);
                    close_fd(child_to_parent[0]);
                    close_fd(child_to_parent[1]);
                    throw std::runtime_error(
                        std::string(description) + ": " +
                        std::strerror(action_rc));
                }
            };
        add_action(
            ::posix_spawn_file_actions_adddup2(
                &actions, parent_to_child[0], STDIN_FILENO),
            "configure worker stdin");
        add_action(
            ::posix_spawn_file_actions_adddup2(
                &actions, child_to_parent[1], STDOUT_FILENO),
            "configure worker stdout");
        add_action(
            ::posix_spawn_file_actions_addclose(
                &actions, parent_to_child[0]),
            "close worker inherited stdin pipe");
        add_action(
            ::posix_spawn_file_actions_addclose(
                &actions, parent_to_child[1]),
            "close worker inherited parent input pipe");
        add_action(
            ::posix_spawn_file_actions_addclose(
                &actions, child_to_parent[0]),
            "close worker inherited parent output pipe");
        add_action(
            ::posix_spawn_file_actions_addclose(
                &actions, child_to_parent[1]),
            "close worker inherited stdout pipe");

        const auto executable = launch.executable.string();
        std::vector<std::string> argument_storage;
        argument_storage.reserve(launch.arguments.size() + 1);
        argument_storage.push_back(executable);
        argument_storage.insert(
            argument_storage.end(), launch.arguments.begin(),
            launch.arguments.end());
        std::vector<char*> argv;
        argv.reserve(argument_storage.size() + 1);
        for (auto& argument : argument_storage) {
            argv.push_back(argument.data());
        }
        argv.push_back(nullptr);

        pid_t child = -1;
        rc = ::posix_spawn(
            &child, executable.c_str(), &actions,
            nullptr, argv.data(), environ);
        ::posix_spawn_file_actions_destroy(&actions);
        if (rc != 0) {
            close_fd(parent_to_child[0]);
            close_fd(parent_to_child[1]);
            close_fd(child_to_parent[0]);
            close_fd(child_to_parent[1]);
            throw std::runtime_error(
                "spawn worker failed: " +
                std::string(std::strerror(rc)));
        }

        pid = child;
        ::close(parent_to_child[0]);
        parent_to_child[0] = -1;
        ::close(child_to_parent[1]);
        child_to_parent[1] = -1;
        input_fd = parent_to_child[1];
        output_fd = child_to_parent[0];
        try {
            set_nonblocking(input_fd);
            set_nonblocking(output_fd);
        } catch (...) {
            terminate();
            throw;
        }
#endif
        last_used_ms = now_ms();
    }

    ~WorkerProcess() { terminate(); }

    bool alive() {
#ifdef _WIN32
        if (process_handle == nullptr) return false;
        const auto result =
            ::WaitForSingleObject(process_handle, 0);
        if (result == WAIT_TIMEOUT) return true;
        if (result == WAIT_OBJECT_0) {
            DWORD code = 0;
            if (::GetExitCodeProcess(process_handle, &code)) {
                last_exit_code = code;
            }
            ::CloseHandle(process_handle);
            process_handle = nullptr;
            process_id = 0;
            close_fd(input_fd);
            close_fd(output_fd);
            return false;
        }
        return true;
#else
        if (pid < 0) return false;
        int status = 0;
        const auto result = ::waitpid(pid, &status, WNOHANG);
        if (result == 0) return true;
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
#endif
    }

    std::string exit_description() const {
#ifdef _WIN32
        if (last_exit_code) {
            return "exit code " +
                std::to_string(*last_exit_code);
        }
#else
        if (last_wait_status) {
            return wait_status_description(*last_wait_status);
        }
#endif
        return "worker process exited";
    }

    void terminate() noexcept {
#ifdef _WIN32
        if (process_handle != nullptr) {
            ::TerminateProcess(process_handle, 137);
            ::WaitForSingleObject(process_handle, INFINITE);
            DWORD code = 0;
            if (::GetExitCodeProcess(process_handle, &code)) {
                last_exit_code = code;
            }
            ::CloseHandle(process_handle);
            process_handle = nullptr;
            process_id = 0;
        }
#else
        if (pid >= 0) {
            ::kill(pid, SIGKILL);
            int status = 0;
            while (::waitpid(pid, &status, 0) < 0 &&
                   errno == EINTR) {
            }
            last_wait_status = status;
            pid = -1;
        }
#endif
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
            return {
                WorkerOutcomeKind::Crashed, {},
                "WORKER_CRASH: " + exit_description()};
        }

        std::vector<std::uint8_t> request_bytes;
        try {
            request_bytes = encode_frame(Frame{
                MessageType::WorkerExecute,
                correlation_id,
                {{"engine_id", engine_id},
                 {"attempt_started_at_ms",
                  std::to_string(attempt_started_at_ms)}},
                payload,
            });
        } catch (const std::exception& ex) {
            terminate();
            return {
                WorkerOutcomeKind::TechnicalFailure, {},
                std::string("WORKER_PROTOCOL_FAILURE: ") + ex.what()};
        }

        const auto timeout_outcome = [&]() -> WorkerOutcome {
            terminate();
            const std::string failure = timeout_wins
                ? "ATTEMPT_TIMEOUT: per-attempt timeout expired"
                : "DEADLINE_EXPIRED: Work deadline expired during attempt";
            return {WorkerOutcomeKind::TimedOut, {}, failure};
        };

        std::size_t request_offset = 0;
        while (request_offset < request_bytes.size()) {
            if (kernel_stopping()) {
                terminate();
                return {WorkerOutcomeKind::Stopped, {}, {}};
            }
            if (cancellation_requested()) {
                terminate();
                return {
                    WorkerOutcomeKind::Cancelled, {}, "cancelled"};
            }
            if (stop_at_ms && now_ms() >= *stop_at_ms) {
                return timeout_outcome();
            }
            if (!alive()) {
                return {
                    WorkerOutcomeKind::Crashed, {},
                    "WORKER_CRASH: " + exit_description()};
            }

#ifdef _WIN32
            const auto amount = static_cast<unsigned int>(
                std::min<std::size_t>(
                    request_bytes.size() - request_offset,
                    static_cast<std::size_t>(INT_MAX)));
            const int n = ::_write(
                input_fd,
                request_bytes.data() + request_offset,
                amount);
#else
            const auto n = ::write(
                input_fd,
                request_bytes.data() + request_offset,
                request_bytes.size() - request_offset);
#endif
            if (n > 0) {
                request_offset += static_cast<std::size_t>(n);
                continue;
            }
#ifdef _WIN32
            if (n < 0) {
                if (!alive()) {
                    return {
                        WorkerOutcomeKind::Crashed, {},
                        "WORKER_CRASH: " +
                            exit_description()};
                }
                terminate();
                return {
                    WorkerOutcomeKind::Crashed, {},
                    "WORKER_IPC_FAILURE: request write failed: " +
                        std::string(std::strerror(errno))};
            }
            std::this_thread::sleep_for(5ms);
#else
            if (n < 0 && errno != EAGAIN &&
                errno != EWOULDBLOCK && errno != EINTR) {
                if (!alive()) {
                    return {
                        WorkerOutcomeKind::Crashed, {},
                        "WORKER_CRASH: " +
                            exit_description()};
                }
                terminate();
                return {
                    WorkerOutcomeKind::Crashed, {},
                    "WORKER_IPC_FAILURE: request write failed: " +
                        std::string(std::strerror(errno))};
            }
            pollfd writable{
                input_fd, POLLOUT | POLLHUP | POLLERR, 0};
            const int poll_result =
                ::poll(&writable, 1, 10);
            if (poll_result < 0 && errno != EINTR) {
                terminate();
                return {
                    WorkerOutcomeKind::Crashed, {},
                    "WORKER_IPC_FAILURE: request poll failed: " +
                        std::string(std::strerror(errno))};
            }
#endif
        }

        const auto finish_response =
            [&](Frame response) -> WorkerOutcome {
                if (response.correlation_id != correlation_id) {
                    terminate();
                    return {
                        WorkerOutcomeKind::TechnicalFailure, {},
                        "WORKER_PROTOCOL_FAILURE: correlation id mismatch"};
                }
                if (response.type == MessageType::WorkerResult) {
                    return {
                        WorkerOutcomeKind::Succeeded,
                        std::move(response.payload), {}};
                }
                if (response.type == MessageType::WorkerFailure) {
                    const auto it =
                        response.metadata.find("technical_failure");
                    return {
                        WorkerOutcomeKind::TechnicalFailure, {},
                        it == response.metadata.end()
                            ? "WORKER_PROTOCOL_FAILURE: missing technical failure"
                            : it->second};
                }
                terminate();
                return {
                    WorkerOutcomeKind::TechnicalFailure, {},
                    "WORKER_PROTOCOL_FAILURE: unexpected worker response"};
            };

        while (true) {
            try {
                if (auto response =
                        response_reader.read_available(output_fd)) {
                    return finish_response(std::move(*response));
                }
            } catch (const std::exception& ex) {
                if (!alive()) {
                    return {
                        WorkerOutcomeKind::Crashed, {},
                        "WORKER_CRASH: " + exit_description()};
                }
                terminate();
                return {
                    WorkerOutcomeKind::TechnicalFailure, {},
                    std::string("WORKER_PROTOCOL_FAILURE: ") +
                        ex.what()};
            }

            if (kernel_stopping()) {
                terminate();
                return {WorkerOutcomeKind::Stopped, {}, {}};
            }

            if (cancellation_requested()) {
                try {
                    if (auto response =
                            response_reader.read_available(output_fd)) {
                        return finish_response(
                            std::move(*response));
                    }
                } catch (const std::exception& ex) {
                    if (!alive()) {
                        return {
                            WorkerOutcomeKind::Crashed, {},
                            "WORKER_CRASH: " +
                                exit_description()};
                    }
                    terminate();
                    return {
                        WorkerOutcomeKind::TechnicalFailure, {},
                        std::string("WORKER_PROTOCOL_FAILURE: ") +
                            ex.what()};
                }
                terminate();
                return {
                    WorkerOutcomeKind::Cancelled, {}, "cancelled"};
            }

            if (stop_at_ms && now_ms() >= *stop_at_ms) {
                try {
                    if (auto response =
                            response_reader.read_available(output_fd)) {
                        return finish_response(
                            std::move(*response));
                    }
                } catch (const std::exception& ex) {
                    if (!alive()) {
                        return {
                            WorkerOutcomeKind::Crashed, {},
                            "WORKER_CRASH: " +
                                exit_description()};
                    }
                    terminate();
                    return {
                        WorkerOutcomeKind::TechnicalFailure, {},
                        std::string("WORKER_PROTOCOL_FAILURE: ") +
                            ex.what()};
                }
                return timeout_outcome();
            }

            if (!alive()) {
                return {
                    WorkerOutcomeKind::Crashed, {},
                    "WORKER_CRASH: " + exit_description()};
            }

#ifdef _WIN32
            std::this_thread::sleep_for(10ms);
#else
            pollfd ready{
                output_fd, POLLIN | POLLHUP | POLLERR, 0};
            const int poll_result = ::poll(&ready, 1, 10);
            if (poll_result < 0 && errno != EINTR) {
                terminate();
                return {
                    WorkerOutcomeKind::Crashed, {},
                    "WORKER_IPC_FAILURE: poll failed: " +
                        std::string(std::strerror(errno))};
            }
#endif
        }
    }

    std::string engine_id;
    IncrementalFrameReader response_reader;
#ifdef _WIN32
    HANDLE process_handle{nullptr};
    DWORD process_id{};
    std::optional<DWORD> last_exit_code;
#else
    pid_t pid{-1};
    std::optional<int> last_wait_status;
#endif
    int input_fd{-1};
    int output_fd{-1};
    bool busy{};
    std::int64_t last_used_ms{};
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
    if (!worker_) return -1;
#ifdef _WIN32
    return static_cast<int>(worker_->process_id);
#else
    return static_cast<int>(worker_->pid);
#endif
}

WorkerPool::WorkerPool(WorkerLaunchTable launch_specs, std::int64_t idle_timeout_ms)
    : launch_specs_(std::move(launch_specs)),
      idle_timeout_ms_(idle_timeout_ms) {
#ifndef _WIN32
    std::signal(SIGPIPE, SIG_IGN);
#endif
    if (idle_timeout_ms_ < 0) {
        throw std::invalid_argument("worker idle timeout must be nonnegative");
    }
    for (const auto& [engine_id, launch] : launch_specs_) {
        if (engine_id.empty() || launch.executable.empty()) {
            throw std::invalid_argument(
                "worker launch configuration requires engine identity and executable");
        }
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

    const auto launch = launch_specs_.find(engine_id);
    if (launch == launch_specs_.end()) {
        throw std::runtime_error("no worker launch configuration for engine " + engine_id);
    }
    auto worker = std::make_shared<WorkerProcess>(launch->second, engine_id);
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
