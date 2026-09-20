#include "protocol.hpp"
#include "store.hpp"

#include <algorithm>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <csignal>
#include <cstring>
#include <filesystem>
#include <fcntl.h>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <mutex>
#include <optional>
#include <poll.h>
#include <random>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <sys/file.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <thread>
#include <unistd.h>
#include <vector>

namespace fs = std::filesystem;
using namespace std::chrono_literals;

namespace madre::kernel {
namespace {
std::atomic_bool g_stop{false};

void on_signal(int) {
    g_stop.store(true);
}

std::int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::system_clock::now().time_since_epoch())
        .count();
}

std::string make_work_id() {
    std::random_device rd;
    std::mt19937_64 generator(rd());
    std::uniform_int_distribution<std::uint64_t> distribution;
    std::ostringstream out;
    out << std::hex << std::setfill('0') << std::setw(16) << distribution(generator)
        << std::setw(16) << distribution(generator);
    return out.str();
}

void require_c1_payload_size(std::uintmax_t size, std::string_view description) {
    if (size > kMaxC1TextGenerationOpaquePayloadBytes) {
        throw std::runtime_error(std::string(description) +
                                 " exceeds the C1 1 MiB bounded text-generation/v1 payload limit");
    }
}

void write_binary_atomic(const fs::path& path, const std::vector<std::uint8_t>& data) {
    require_c1_payload_size(data.size(), "C1 file-backed payload");
    fs::create_directories(path.parent_path());
    const auto temporary = path.string() + ".tmp";
    {
        std::ofstream stream(temporary, std::ios::binary | std::ios::trunc);
        if (!stream) {
            throw std::runtime_error("open payload file for write: " + temporary);
        }
        if (!data.empty()) {
            stream.write(reinterpret_cast<const char*>(data.data()), static_cast<std::streamsize>(data.size()));
        }
        stream.flush();
        if (!stream) {
            throw std::runtime_error("write payload file: " + temporary);
        }
    }
    fs::rename(temporary, path);
}

std::vector<std::uint8_t> read_binary_bounded(const fs::path& path) {
    std::error_code size_error;
    const auto size = fs::file_size(path, size_error);
    if (size_error) {
        throw std::runtime_error("inspect payload file size: " + path.string());
    }
    require_c1_payload_size(size, "C1 file-backed payload");

    std::ifstream stream(path, std::ios::binary);
    if (!stream) {
        throw std::runtime_error("open payload file for read: " + path.string());
    }
    std::vector<std::uint8_t> data(static_cast<std::size_t>(size));
    if (!data.empty()) {
        stream.read(reinterpret_cast<char*>(data.data()), static_cast<std::streamsize>(data.size()));
        if (!stream) {
            throw std::runtime_error("read payload file: " + path.string());
        }
    }
    return data;
}

Frame response(MessageType type, const Frame& request) {
    return Frame{type, request.correlation_id, {}, {}};
}

Frame error_response(const Frame& request, std::string code, std::string message) {
    Frame out{MessageType::Error, request.correlation_id, {}, {}};
    out.metadata.emplace("code", std::move(code));
    out.metadata.emplace("message", std::move(message));
    return out;
}

class EndpointLock {
public:
    explicit EndpointLock(const fs::path& endpoint) {
        lock_path_ = endpoint;
        lock_path_ += ".lock";
        if (!lock_path_.parent_path().empty()) {
            fs::create_directories(lock_path_.parent_path());
        }
        fd_ = ::open(lock_path_.c_str(), O_CREAT | O_RDWR, S_IRUSR | S_IWUSR);
        if (fd_ < 0) {
            throw std::runtime_error("open Kernel endpoint lock failed: " +
                                     std::string(std::strerror(errno)));
        }
        if (::flock(fd_, LOCK_EX | LOCK_NB) != 0) {
            const int lock_error = errno;
            ::close(fd_);
            fd_ = -1;
            if (lock_error == EWOULDBLOCK || lock_error == EAGAIN) {
                throw std::runtime_error("Kernel endpoint lock is already held");
            }
            throw std::runtime_error("acquire Kernel endpoint lock failed: " +
                                     std::string(std::strerror(lock_error)));
        }
    }

    EndpointLock(const EndpointLock&) = delete;
    EndpointLock& operator=(const EndpointLock&) = delete;

    ~EndpointLock() {
        if (fd_ >= 0) {
            ::close(fd_);
        }
    }

private:
    fs::path lock_path_;
    int fd_{-1};
};

std::vector<std::string> split_csv(std::string_view csv) {
    std::vector<std::string> values;
    std::size_t pos = 0;
    while (pos <= csv.size()) {
        const auto comma = csv.find(',', pos);
        const auto end = comma == std::string_view::npos ? csv.size() : comma;
        if (end > pos) {
            values.emplace_back(csv.substr(pos, end - pos));
        }
        if (comma == std::string_view::npos) {
            break;
        }
        pos = comma + 1;
    }
    return values;
}

std::string join_csv(const std::vector<std::string>& values) {
    std::string out;
    for (std::size_t i = 0; i < values.size(); ++i) {
        if (i != 0) {
            out += ',';
        }
        out += values[i];
    }
    return out;
}

bool contains(const std::vector<std::string>& values, std::string_view value) {
    return std::find(values.begin(), values.end(), value) != values.end();
}

bool contains_all(const std::vector<std::string>& available, const std::vector<std::string>& required) {
    return std::all_of(required.begin(), required.end(), [&](const std::string& item) {
        return contains(available, item);
    });
}

struct EngineDescriptorFacts {
    std::string id;
    std::vector<std::string> work_types;
    std::vector<std::string> capabilities;
    std::vector<std::string> model_ids;
    std::vector<std::string> supported_efforts;
    std::string placement;
    std::string availability;
    bool warm;
};

const std::vector<EngineDescriptorFacts>& engine_inventory() {
    static const std::vector<EngineDescriptorFacts> inventory{
        {"fake-standard",
         {"text-generation/v1"},
         {"basic-text"},
         {"standard-v1", "shared-v1"},
         {"STANDARD"},
         "KERNEL_PROCESS",
         "AVAILABLE",
         true},
        {"fake-capable",
         {"text-generation/v1"},
         {"basic-text", "structured-output", "long-context"},
         {"high-v1", "shared-v1"},
         {"STANDARD", "HIGH"},
         "KERNEL_PROCESS",
         "AVAILABLE",
         true},
        {"fake-vision",
         {"text-generation/v1"},
         {"basic-text", "image-input"},
         {"vision-v1"},
         {"HIGH"},
         "KERNEL_PROCESS",
         "AVAILABLE",
         false},
    };
    return inventory;
}

void add_engine_descriptor(Frame& frame, std::string_view prefix, const EngineDescriptorFacts& descriptor) {
    const std::string base(prefix);
    frame.metadata[base + "id"] = descriptor.id;
    frame.metadata[base + "work_types"] = join_csv(descriptor.work_types);
    frame.metadata[base + "capabilities"] = join_csv(descriptor.capabilities);
    frame.metadata[base + "model_ids"] = join_csv(descriptor.model_ids);
    frame.metadata[base + "supported_efforts"] = join_csv(descriptor.supported_efforts);
    frame.metadata[base + "placement"] = descriptor.placement;
    frame.metadata[base + "availability"] = descriptor.availability;
    frame.metadata[base + "warm"] = descriptor.warm ? "true" : "false";
}

struct Selection {
    const EngineDescriptorFacts* engine{};
    std::string model_id;
};

struct SelectionResult {
    std::optional<Selection> selection;
    std::string failure;
};

SelectionResult select_engine(const WorkRecord& work) {
    std::vector<const EngineDescriptorFacts*> candidates;
    for (const auto& engine : engine_inventory()) {
        candidates.push_back(&engine);
    }

    std::erase_if(candidates, [&](const auto* engine) {
        return !contains(engine->work_types, work.work_type);
    });
    if (candidates.empty()) {
        return {std::nullopt, "NO_ELIGIBLE_ENGINE: no engine supports Work type " + work.work_type};
    }

    const auto required_capabilities = split_csv(work.required_capabilities);
    std::erase_if(candidates, [&](const auto* engine) {
        return !contains_all(engine->capabilities, required_capabilities);
    });
    if (candidates.empty()) {
        return {std::nullopt, "NO_ELIGIBLE_ENGINE: required capabilities are not satisfied"};
    }

    const auto allowlist = split_csv(work.eligible_engine_ids);
    if (!allowlist.empty()) {
        std::erase_if(candidates, [&](const auto* engine) {
            return !contains(allowlist, engine->id);
        });
    }
    if (candidates.empty()) {
        return {std::nullopt, "NO_ELIGIBLE_ENGINE: eligible engine allowlist excluded all compatible engines"};
    }

    if (!work.exact_engine_id.empty()) {
        std::erase_if(candidates, [&](const auto* engine) {
            return engine->id != work.exact_engine_id;
        });
    }
    if (!work.exact_model_id.empty()) {
        std::erase_if(candidates, [&](const auto* engine) {
            return !contains(engine->model_ids, work.exact_model_id);
        });
    }
    if (candidates.empty()) {
        return {std::nullopt, "EXACT_SELECTION_UNSATISFIED: exact engine/model constraint did not match"};
    }

    std::erase_if(candidates, [&](const auto* engine) {
        return !contains(engine->supported_efforts, work.effort);
    });
    if (candidates.empty()) {
        return {std::nullopt, "NO_ELIGIBLE_ENGINE: requested effort " + work.effort + " is unsupported"};
    }

    std::erase_if(candidates, [](const auto* engine) {
        return engine->availability != "AVAILABLE";
    });
    if (candidates.empty()) {
        return {std::nullopt, "NO_ELIGIBLE_ENGINE: compatible engines are unavailable"};
    }

    const auto* selected = candidates.front();
    const auto model = work.exact_model_id.empty() ? selected->model_ids.front() : work.exact_model_id;
    return {Selection{selected, model}, {}};
}

std::string optional_metadata(const Frame& frame, const std::string& key) {
    const auto it = frame.metadata.find(key);
    return it == frame.metadata.end() ? std::string{} : it->second;
}

std::int64_t parse_i64(const std::string& value, const char* field) {
    try {
        std::size_t used = 0;
        const auto parsed = std::stoll(value, &used);
        if (used != value.size()) {
            throw std::invalid_argument("trailing characters");
        }
        return parsed;
    } catch (const std::exception&) {
        throw std::runtime_error(std::string("invalid integer metadata field: ") + field);
    }
}

std::optional<std::int64_t> optional_nonnegative_i64(const Frame& frame, const std::string& key) {
    const auto value = optional_metadata(frame, key);
    if (value.empty()) {
        return std::nullopt;
    }
    const auto parsed = parse_i64(value, key.c_str());
    if (parsed < 0) {
        throw std::runtime_error(key + " must be >= 0");
    }
    return parsed;
}

int parse_positive_int(const Frame& frame, const std::string& key) {
    const auto parsed = parse_i64(metadata_value(frame, key), key.c_str());
    if (parsed < 1 || parsed > 1000000) {
        throw std::runtime_error(key + " must be between 1 and 1000000");
    }
    return static_cast<int>(parsed);
}

void validate_choice(std::string_view value, std::string_view a, std::string_view b, const char* field) {
    if (value != a && value != b) {
        throw std::runtime_error(std::string("invalid ") + field);
    }
}

void validate_urgency(std::string_view value) {
    if (value != "INTERACTIVE" && value != "NORMAL" && value != "BACKGROUND") {
        throw std::runtime_error("invalid urgency");
    }
}

class Kernel {
public:
    Kernel(fs::path data_dir, fs::path endpoint, int fake_delay_ms)
        : data_dir_(std::move(data_dir)),
          endpoint_(std::move(endpoint)),
          endpoint_lock_(endpoint_),
          store_(data_dir_ / "kernel.db"),
          fake_delay_ms_(fake_delay_ms) {
        fs::create_directories(data_dir_ / "work");
        store_.recover_interrupted(now_ms());
    }

    ~Kernel() {
        stop_.store(true);
        wake_.notify_all();
        if (worker_.joinable()) {
            worker_.join();
        }
        if (server_fd_ >= 0) {
            ::close(server_fd_);
        }
        std::error_code error;
        fs::remove(endpoint_, error);
    }

    void run() {
        open_server();
        start_worker();
        while (!g_stop.load() && !stop_.load()) {
            pollfd ready{server_fd_, POLLIN, 0};
            const int poll_result = ::poll(&ready, 1, 100);
            if (poll_result < 0) {
                if (errno == EINTR) {
                    continue;
                }
                throw std::runtime_error("poll local IPC socket failed: " + std::string(std::strerror(errno)));
            }
            if (poll_result == 0) {
                continue;
            }
            const int fd = ::accept(server_fd_, nullptr, nullptr);
            if (fd < 0) {
                if (errno == EINTR) {
                    continue;
                }
                throw std::runtime_error("accept local IPC client failed: " + std::string(std::strerror(errno)));
            }
            serve_connection(fd);
            ::close(fd);
        }
    }

private:
    void open_server() {
        if (endpoint_.string().size() >= sizeof(sockaddr_un::sun_path)) {
            throw std::runtime_error("Unix-domain socket path is too long");
        }
        fs::create_directories(endpoint_.parent_path());
        std::error_code error;
        fs::remove(endpoint_, error);

        server_fd_ = ::socket(AF_UNIX, SOCK_STREAM, 0);
        if (server_fd_ < 0) {
            throw std::runtime_error("create Unix-domain socket failed");
        }
        sockaddr_un address{};
        address.sun_family = AF_UNIX;
        std::snprintf(address.sun_path, sizeof(address.sun_path), "%s", endpoint_.c_str());
        if (::bind(server_fd_, reinterpret_cast<sockaddr*>(&address), sizeof(address)) != 0) {
            throw std::runtime_error("bind Unix-domain socket failed: " + std::string(std::strerror(errno)));
        }
        if (::chmod(endpoint_.c_str(), S_IRUSR | S_IWUSR) != 0) {
            throw std::runtime_error("set owner-only Unix-domain socket permissions failed");
        }
        if (::listen(server_fd_, 16) != 0) {
            throw std::runtime_error("listen on Unix-domain socket failed");
        }
    }

    void start_worker() {
        worker_ = std::thread([this] { worker_loop(); });
    }

    void worker_loop() {
        while (!stop_.load()) {
            const auto scheduler_now = now_ms();
            store_.expire_queued_deadlines(scheduler_now);
            auto work = store_.next_eligible(scheduler_now);
            if (!work) {
                std::unique_lock lock(wake_mutex_);
                wake_.wait_for(lock, 20ms, [this] { return stop_.load(); });
                continue;
            }
            if (work->deadline_ms && scheduler_now >= *work->deadline_ms) {
                store_.fail_queued(work->id, "DEADLINE_EXPIRED: Work deadline expired before dispatch");
                continue;
            }

            const auto selection = select_engine(*work);
            if (!selection.selection) {
                store_.fail_queued(work->id, selection.failure);
                continue;
            }

            const auto started_at = now_ms();
            if (work->deadline_ms && started_at >= *work->deadline_ms) {
                store_.fail_queued(work->id, "DEADLINE_EXPIRED: Work deadline expired before dispatch");
                continue;
            }
            const auto& selected = *selection.selection;
            const int attempt_number = store_.begin_attempt(
                work->id, selected.engine->id, selected.model_id, started_at);
            if (attempt_number == 0) {
                continue;
            }

            try {
                execute_fake(*work, attempt_number, started_at);
            } catch (const std::exception& ex) {
                store_.finish_retryable_failure(
                    work->id, attempt_number, "FAILED", ex.what(), now_ms());
            }
        }
    }

    void execute_fake(const WorkRecord& work, int attempt_number, std::int64_t started_at_ms) {
        std::optional<std::int64_t> stop_at;
        bool timeout_wins = false;
        if (work.timeout_ms) {
            stop_at = started_at_ms + *work.timeout_ms;
            timeout_wins = true;
        }
        if (work.deadline_ms && (!stop_at || *work.deadline_ms < *stop_at)) {
            stop_at = *work.deadline_ms;
            timeout_wins = false;
        }

        const auto complete_at = started_at_ms + fake_delay_ms_;
        while (now_ms() < complete_at) {
            if (stop_.load()) {
                return;
            }
            if (store_.cancel_requested(work.id)) {
                store_.finish_cancelled(work.id, attempt_number, now_ms());
                return;
            }
            const auto current = now_ms();
            if (stop_at && current >= *stop_at) {
                const std::string failure = timeout_wins
                    ? "ATTEMPT_TIMEOUT: per-attempt timeout expired"
                    : "DEADLINE_EXPIRED: Work deadline expired during attempt";
                store_.finish_retryable_failure(work.id, attempt_number, "TIMED_OUT", failure, current);
                return;
            }
            std::this_thread::sleep_for(5ms);
        }

        if (store_.cancel_requested(work.id)) {
            store_.finish_cancelled(work.id, attempt_number, now_ms());
            return;
        }
        const auto finished_at = now_ms();
        if (stop_at && finished_at >= *stop_at) {
            const std::string failure = timeout_wins
                ? "ATTEMPT_TIMEOUT: per-attempt timeout expired"
                : "DEADLINE_EXPIRED: Work deadline expired during attempt";
            store_.finish_retryable_failure(work.id, attempt_number, "TIMED_OUT", failure, finished_at);
            return;
        }

        const auto input = read_binary_bounded(work.input_path);
        constexpr std::string_view forced_failure = "__C2_TECHNICAL_FAILURE__";
        if (input.size() == forced_failure.size() &&
            std::equal(input.begin(), input.end(), forced_failure.begin())) {
            throw std::runtime_error("FAKE_TECHNICAL_FAILURE: deterministic C2 test engine failure");
        }

        constexpr std::string_view prefix = "fake:";
        if (input.size() > kMaxC1TextGenerationOpaquePayloadBytes - prefix.size()) {
            throw std::runtime_error(
                "fake engine result exceeds the C1 1 MiB bounded text-generation/v1 payload limit");
        }
        std::vector<std::uint8_t> result(prefix.begin(), prefix.end());
        result.insert(result.end(), input.begin(), input.end());
        write_binary_atomic(work.result_path, result);
        store_.finish_success(work.id, attempt_number, now_ms());
    }

    void serve_connection(int fd) {
        Frame hello_request{};
        try {
            hello_request = read_frame(fd);
            if (hello_request.type != MessageType::Hello) {
                write_frame(fd, error_response(hello_request, "HELLO_REQUIRED", "HELLO must be the first frame"));
                return;
            }
            const auto hello_response = hello(hello_request);
            write_frame(fd, hello_response);
            if (hello_response.type == MessageType::Error) {
                return;
            }
            const auto request = read_frame(fd);
            write_frame(fd, handle(request));
        } catch (const FramingError& ex) {
            try {
                write_frame(fd, error_response(hello_request, "FRAMING_ERROR", ex.what()));
            } catch (...) {
            }
        } catch (const std::exception& ex) {
            try {
                write_frame(fd, error_response(hello_request, "PROTOCOL_OR_REQUEST_ERROR", ex.what()));
            } catch (...) {
            }
        }
    }

    Frame handle(const Frame& request) {
        switch (request.type) {
            case MessageType::Submit: return submit(request);
            case MessageType::Status: return status(request);
            case MessageType::Result: return result(request);
            case MessageType::Acknowledge: return acknowledge(request);
            case MessageType::Cancel: return cancel(request);
            case MessageType::ListEngines: return list_engines(request);
            case MessageType::EngineStatus: return engine_status(request);
            default: return error_response(request, "UNKNOWN_MESSAGE", "unsupported Kernel command");
        }
    }

    Frame hello(const Frame& request) {
        const auto client_min = std::stoi(metadata_value(request, "min_kernel_protocol_version"));
        const auto client_max = std::stoi(metadata_value(request, "max_kernel_protocol_version"));
        const auto overlap_min = std::max(client_min, kMinKernelProtocolVersion);
        const auto overlap_max = std::min(client_max, kMaxKernelProtocolVersion);
        if (client_min > client_max || overlap_min > overlap_max) {
            return error_response(request, "VERSION_MISMATCH", "no compatible Kernel protocol/API version");
        }
        const auto negotiated = overlap_max;
        auto out = response(MessageType::HelloResponse, request);
        out.metadata["kernel_protocol_version"] = std::to_string(negotiated);
        return out;
    }

    Frame submit(const Frame& request) {
        if (request.payload.size() > kMaxC1TextGenerationOpaquePayloadBytes) {
            return error_response(
                request,
                "PAYLOAD_TOO_LARGE",
                "C1 text-generation/v1 input exceeds the 1 MiB bounded payload limit; streaming/spooling is deferred");
        }

        const auto work_type = metadata_value(request, "work_type");
        const auto effort = metadata_value(request, "effort");
        const auto urgency = metadata_value(request, "urgency");
        validate_choice(effort, "STANDARD", "HIGH", "effort");
        validate_urgency(urgency);
        const auto max_attempts = parse_positive_int(request, "max_attempts");
        const auto retry_delay_ms = parse_i64(metadata_value(request, "retry_delay_ms"), "retry_delay_ms");
        if (retry_delay_ms < 0) {
            throw std::runtime_error("retry_delay_ms must be >= 0");
        }

        const auto submitted_at = now_ms();
        const auto eligible_at = optional_nonnegative_i64(request, "eligible_at_ms").value_or(submitted_at);
        const auto deadline = optional_nonnegative_i64(request, "deadline_ms");
        const auto timeout = optional_nonnegative_i64(request, "timeout_ms");
        if (timeout && *timeout == 0) {
            throw std::runtime_error("timeout_ms must be > 0 when supplied");
        }

        const auto id = make_work_id();
        const auto work_dir = data_dir_ / "work" / id;
        const auto input = work_dir / "input.bin";
        const auto result_path = work_dir / "result.bin";
        write_binary_atomic(input, request.payload);
        store_.submit(WorkRecord{
            id,
            work_type,
            "QUEUED",
            effort,
            urgency,
            optional_metadata(request, "required_capabilities"),
            optional_metadata(request, "eligible_engine_ids"),
            optional_metadata(request, "exact_engine_id"),
            optional_metadata(request, "exact_model_id"),
            submitted_at,
            eligible_at,
            eligible_at,
            deadline,
            timeout,
            max_attempts,
            retry_delay_ms,
            0,
            input,
            result_path,
            false,
            false,
            {},
            {},
            {},
        });
        wake_.notify_one();
        auto out = response(MessageType::SubmitResponse, request);
        out.metadata["work_id"] = id;
        return out;
    }

    Frame status(const Frame& request) {
        const auto id = metadata_value(request, "work_id");
        const auto work = store_.find(id);
        if (!work) {
            return error_response(request, "WORK_NOT_FOUND", "unknown WorkId");
        }
        auto out = response(MessageType::StatusResponse, request);
        out.metadata["state"] = work->state;
        if (!work->technical_failure.empty()) {
            out.metadata["technical_failure"] = work->technical_failure;
        }
        return out;
    }

    Frame result(const Frame& request) {
        const auto id = metadata_value(request, "work_id");
        const auto work = store_.find(id);
        if (!work) {
            return error_response(request, "WORK_NOT_FOUND", "unknown WorkId");
        }
        auto out = response(MessageType::ResultResponse, request);
        out.metadata["state"] = work->state;
        const bool available = work->state == "SUCCEEDED" && !work->acknowledged && fs::exists(work->result_path);
        out.metadata["available"] = available ? "true" : "false";
        if (available) {
            const auto result_size = fs::file_size(work->result_path);
            if (result_size > kMaxC1TextGenerationOpaquePayloadBytes) {
                return error_response(
                    request,
                    "PAYLOAD_TOO_LARGE",
                    "C1 text-generation/v1 result exceeds the 1 MiB bounded payload limit; streaming/spooling is deferred");
            }
            out.payload = read_binary_bounded(work->result_path);
        }
        return out;
    }

    Frame acknowledge(const Frame& request) {
        const auto id = metadata_value(request, "work_id");
        const auto work = store_.find(id);
        if (!work) {
            return error_response(request, "WORK_NOT_FOUND", "unknown WorkId");
        }
        if (!store_.acknowledge(id)) {
            return error_response(request, "RESULT_NOT_ACKNOWLEDGEABLE", "result is not available for acknowledgement");
        }
        std::error_code error;
        fs::remove(work->input_path, error);
        fs::remove(work->result_path, error);
        auto out = response(MessageType::AcknowledgeResponse, request);
        out.metadata["acknowledged"] = "true";
        return out;
    }

    Frame cancel(const Frame& request) {
        const auto id = metadata_value(request, "work_id");
        const auto state = store_.cancel(id);
        if (state.empty()) {
            return error_response(request, "WORK_NOT_FOUND", "unknown WorkId");
        }
        wake_.notify_one();
        auto out = response(MessageType::CancelResponse, request);
        out.metadata["state"] = state;
        return out;
    }

    Frame list_engines(const Frame& request) {
        auto out = response(MessageType::ListEnginesResponse, request);
        const auto& inventory = engine_inventory();
        out.metadata["count"] = std::to_string(inventory.size());
        for (std::size_t i = 0; i < inventory.size(); ++i) {
            add_engine_descriptor(out, "engine." + std::to_string(i) + ".", inventory[i]);
        }
        return out;
    }

    Frame engine_status(const Frame& request) {
        const auto id = metadata_value(request, "engine_id");
        const auto& inventory = engine_inventory();
        const auto it = std::find_if(inventory.begin(), inventory.end(), [&](const auto& descriptor) {
            return descriptor.id == id;
        });
        if (it == inventory.end()) {
            return error_response(request, "ENGINE_NOT_FOUND", "unknown physical engine");
        }
        auto out = response(MessageType::EngineStatusResponse, request);
        add_engine_descriptor(out, "", *it);
        return out;
    }

    fs::path data_dir_;
    fs::path endpoint_;
    EndpointLock endpoint_lock_;
    WorkStore store_;
    int fake_delay_ms_;
    int server_fd_{-1};
    std::atomic_bool stop_{false};
    std::thread worker_;
    std::mutex wake_mutex_;
    std::condition_variable wake_;
};

struct Options {
    fs::path data_dir;
    fs::path endpoint;
    int fake_delay_ms{250};
};

Options parse_options(int argc, char** argv) {
    Options options;
    for (int i = 1; i < argc; ++i) {
        const std::string argument = argv[i];
        if (argument == "--data-dir" && i + 1 < argc) {
            options.data_dir = argv[++i];
        } else if (argument == "--endpoint" && i + 1 < argc) {
            options.endpoint = argv[++i];
        } else if (argument == "--fake-delay-ms" && i + 1 < argc) {
            options.fake_delay_ms = std::stoi(argv[++i]);
        } else {
            throw std::runtime_error("usage: madre-kernel --data-dir <path> --endpoint <unix-socket> [--fake-delay-ms N]");
        }
    }
    if (options.data_dir.empty() || options.endpoint.empty() || options.fake_delay_ms < 0) {
        throw std::runtime_error("usage: madre-kernel --data-dir <path> --endpoint <unix-socket> [--fake-delay-ms N]");
    }
    return options;
}

}  // namespace
}  // namespace madre::kernel

int main(int argc, char** argv) {
    try {
        std::signal(SIGINT, madre::kernel::on_signal);
        std::signal(SIGTERM, madre::kernel::on_signal);
        std::signal(SIGPIPE, SIG_IGN);
        const auto options = madre::kernel::parse_options(argc, argv);
        madre::kernel::Kernel kernel(options.data_dir, options.endpoint, options.fake_delay_ms);
        kernel.run();
        return 0;
    } catch (const std::exception& ex) {
        std::cerr << "madre-kernel: " << ex.what() << '\n';
        return 1;
    }
}
