#include "local_ipc.hpp"
#include "process_executor.hpp"
#include "protocol.hpp"
#include "store.hpp"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <csignal>
#include <filesystem>
#include <fstream>
#include <future>
#include <iomanip>
#include <iostream>
#include <limits>
#include <mutex>
#include <optional>
#include <random>
#include <set>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <thread>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#endif

namespace fs = std::filesystem;
using namespace std::chrono_literals;

namespace madre::kernel {
namespace {
std::atomic_bool g_stop{false};

void on_signal(int) { g_stop.store(true); }

#ifdef _WIN32
BOOL WINAPI on_console_control(DWORD event) {
    if (event == CTRL_C_EVENT || event == CTRL_BREAK_EVENT || event == CTRL_CLOSE_EVENT || event == CTRL_SHUTDOWN_EVENT) {
        g_stop.store(true);
        return TRUE;
    }
    return FALSE;
}
#endif

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

void require_payload_size(std::uintmax_t size, std::string_view description) {
    if (size > kMaxBoundedPayloadBytes) {
        throw std::runtime_error(std::string(description) + " exceeds the 1 MiB bounded physical payload limit");
    }
}

void write_binary_atomic(const fs::path& path, const std::vector<std::uint8_t>& data) {
    require_payload_size(data.size(), "file-backed payload");
    fs::create_directories(path.parent_path());
    const auto temporary = path.string() + ".tmp";
    {
        std::ofstream stream(temporary, std::ios::binary | std::ios::trunc);
        if (!stream) throw std::runtime_error("open payload file for write: " + temporary);
        if (!data.empty()) stream.write(reinterpret_cast<const char*>(data.data()), static_cast<std::streamsize>(data.size()));
        stream.flush();
        if (!stream) throw std::runtime_error("write payload file: " + temporary);
    }
    std::error_code remove_error;
    fs::remove(path, remove_error);
    fs::rename(temporary, path);
}

std::vector<std::uint8_t> read_binary_bounded(const fs::path& path) {
    std::error_code size_error;
    const auto size = fs::file_size(path, size_error);
    if (size_error) throw std::runtime_error("inspect payload file size: " + path.string());
    require_payload_size(size, "file-backed payload");
    std::ifstream stream(path, std::ios::binary);
    if (!stream) throw std::runtime_error("open payload file for read: " + path.string());
    std::vector<std::uint8_t> data(static_cast<std::size_t>(size));
    if (!data.empty()) {
        stream.read(reinterpret_cast<char*>(data.data()), static_cast<std::streamsize>(data.size()));
        if (!stream) throw std::runtime_error("read payload file: " + path.string());
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

std::string optional_metadata(const Frame& frame, const std::string& key) {
    const auto it = frame.metadata.find(key);
    return it == frame.metadata.end() ? std::string{} : it->second;
}

std::int64_t parse_i64(const std::string& value, const char* field) {
    try {
        std::size_t used = 0;
        const auto parsed = std::stoll(value, &used);
        if (used != value.size()) throw std::invalid_argument("trailing characters");
        return parsed;
    } catch (const std::exception&) {
        throw std::runtime_error(std::string("invalid integer metadata field: ") + field);
    }
}

std::optional<std::int64_t> optional_nonnegative_i64(const Frame& frame, const std::string& key) {
    const auto value = optional_metadata(frame, key);
    if (value.empty()) return std::nullopt;
    const auto parsed = parse_i64(value, key.c_str());
    if (parsed < 0) throw std::runtime_error(key + " must be >= 0");
    return parsed;
}

int parse_positive_int(const Frame& frame, const std::string& key, int maximum = std::numeric_limits<int>::max()) {
    const auto parsed = parse_i64(metadata_value(frame, key), key.c_str());
    if (parsed < 1 || parsed > maximum) throw std::runtime_error(key + " must be within the supported positive range");
    return static_cast<int>(parsed);
}

int parse_nonnegative_int(const Frame& frame, const std::string& key, int maximum) {
    const auto parsed = parse_i64(metadata_value(frame, key), key.c_str());
    if (parsed < 0 || parsed > maximum) throw std::runtime_error(key + " is outside the supported range");
    return static_cast<int>(parsed);
}

void validate_urgency(std::string_view value) {
    if (value != "INTERACTIVE" && value != "NORMAL" && value != "BACKGROUND") throw std::runtime_error("invalid urgency");
}

void validate_retry_safety(std::string_view value) {
    if (value != "NEVER" && value != "DEFINITE_FAILURES" && value != "INCLUDING_UNKNOWN_COMPLETION") {
        throw std::runtime_error("invalid retry_safety");
    }
}

void validate_wire_text(std::string_view value, const char* field, bool allow_empty = false) {
    if ((!allow_empty && value.empty()) || value.find_first_of("\r\n") != std::string_view::npos) {
        throw std::runtime_error(std::string("invalid ") + field);
    }
}

std::string single_line_failure(std::string failure, const std::string& stderr_text) {
    if (!stderr_text.empty()) {
        std::string compact = stderr_text;
        std::replace(compact.begin(), compact.end(), '\r', ' ');
        std::replace(compact.begin(), compact.end(), '\n', ' ');
        if (compact.size() > 512) compact.resize(512);
        failure += ": stderr=" + compact;
    }
    return failure;
}

class Kernel {
public:
    Kernel(fs::path data_dir, fs::path endpoint, int max_concurrent)
        : data_dir_(std::move(data_dir)),
          local_ipc_(std::move(endpoint), data_dir_),
          store_(data_dir_ / "kernel.db"),
          max_concurrent_(max_concurrent) {
        fs::create_directories(data_dir_ / "work");
        store_.recover_interrupted(now_ms());
    }

    ~Kernel() {
        stop_.store(true);
        wake_.notify_all();
        if (scheduler_.joinable()) scheduler_.join();
        for (auto& task : attempt_tasks_) {
            if (task.valid()) task.wait();
        }
    }

    void run() {
        scheduler_ = std::thread([this] { scheduler_loop(); });
        while (!g_stop.load() && !stop_.load()) {
            const int fd = local_ipc_.accept_for(100ms);
            if (fd < 0) continue;
            serve_connection(fd);
            close_local_ipc(fd);
        }
        stop_.store(true);
        wake_.notify_all();
    }

private:
    std::optional<ProcessInvocationSpec> select_candidate(const WorkRecord& work) const {
        for (const auto& candidate : work.candidates) {
            if (process_executor_.executable_available(candidate.executable)) return candidate;
        }
        return std::nullopt;
    }

    void scheduler_loop() {
        while (!stop_.load()) {
            std::erase_if(attempt_tasks_, [](std::future<void>& task) {
                return task.wait_for(0ms) == std::future_status::ready;
            });
            if (active_attempts_.load() >= max_concurrent_) {
                std::unique_lock lock(wake_mutex_);
                wake_.wait_for(lock, 10ms, [this] { return stop_.load(); });
                continue;
            }
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
            const auto selected = select_candidate(*work);
            if (!selected) {
                store_.fail_queued(work->id, "NO_DISPATCHABLE_CANDIDATE: none of the supplied ProcessInvocation executables is available");
                continue;
            }
            const auto started_at = now_ms();
            const int attempt_number = store_.begin_attempt(work->id, *selected, started_at);
            if (attempt_number == 0) continue;
            active_attempts_.fetch_add(1);
            attempt_tasks_.push_back(std::async(
                std::launch::async,
                [this, work = *work, candidate = *selected, attempt_number, started_at]() {
                    execute_process(work, candidate, attempt_number, started_at);
                    active_attempts_.fetch_sub(1);
                    wake_.notify_all();
                }));
        }
    }

    void execute_process(const WorkRecord& work, const ProcessInvocationSpec& candidate,
                         int attempt_number, std::int64_t started_at_ms) {
        try {
            std::optional<std::int64_t> stop_at;
            bool timeout_wins = false;
            if (work.attempt_timeout_ms) {
                stop_at = started_at_ms + *work.attempt_timeout_ms;
                timeout_wins = true;
            }
            if (work.deadline_ms && (!stop_at || *work.deadline_ms < *stop_at)) {
                stop_at = *work.deadline_ms;
                timeout_wins = false;
            }
            const auto input = read_binary_bounded(work.input_path);
            const auto outcome = process_executor_.execute(
                candidate,
                input,
                [this, &work] { return store_.cancel_requested(work.id); },
                [this] { return stop_.load(); },
                stop_at,
                timeout_wins);

            switch (outcome.kind) {
                case ProcessOutcomeKind::Stopped:
                    return;
                case ProcessOutcomeKind::Cancelled:
                    store_.finish_cancelled(work.id, attempt_number, now_ms());
                    return;
                case ProcessOutcomeKind::TimedOut:
                    store_.finish_definite_failure(
                        work.id, attempt_number, "TIMED_OUT",
                        single_line_failure(outcome.technical_failure, outcome.stderr_text),
                        outcome.exit_code, now_ms());
                    return;
                case ProcessOutcomeKind::TechnicalFailure:
                    store_.finish_definite_failure(
                        work.id, attempt_number, "FAILED",
                        single_line_failure(outcome.technical_failure, outcome.stderr_text),
                        outcome.exit_code, now_ms());
                    return;
                case ProcessOutcomeKind::Succeeded:
                    break;
            }

            if (store_.cancel_requested(work.id)) {
                store_.finish_cancelled(work.id, attempt_number, now_ms());
                return;
            }
            write_binary_atomic(work.result_path, outcome.stdout_payload);
            store_.finish_success(work.id, attempt_number, now_ms());
        } catch (const std::exception& ex) {
            if (stop_.load()) return;
            store_.finish_definite_failure(
                work.id, attempt_number, "FAILED",
                std::string("PROCESS_EXECUTION_FAILURE: ") + ex.what(), std::nullopt, now_ms());
        }
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
            if (hello_response.type == MessageType::Error) return;
            const auto request = read_frame(fd);
            write_frame(fd, handle(request));
        } catch (const FramingError& ex) {
            try { write_frame(fd, error_response(hello_request, "FRAMING_ERROR", ex.what())); } catch (...) {}
        } catch (const std::exception& ex) {
            try { write_frame(fd, error_response(hello_request, "PROTOCOL_OR_REQUEST_ERROR", ex.what())); } catch (...) {}
        }
    }

    Frame handle(const Frame& request) {
        switch (request.type) {
            case MessageType::Submit: return submit(request);
            case MessageType::Status: return status(request);
            case MessageType::Result: return result(request);
            case MessageType::Acknowledge: return acknowledge(request);
            case MessageType::Cancel: return cancel(request);
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
        auto out = response(MessageType::HelloResponse, request);
        out.metadata["kernel_protocol_version"] = std::to_string(overlap_max);
        return out;
    }

    Frame submit(const Frame& request) {
        require_payload_size(request.payload.size(), "submitted input");
        const auto urgency = metadata_value(request, "urgency");
        validate_urgency(urgency);
        const auto retry_safety = metadata_value(request, "retry_safety");
        validate_retry_safety(retry_safety);
        const auto max_attempts = parse_positive_int(request, "max_attempts", 1000);
        const auto retry_delay_ms = parse_i64(metadata_value(request, "retry_delay_ms"), "retry_delay_ms");
        if (retry_delay_ms < 0) throw std::runtime_error("retry_delay_ms must be >= 0");
        const int candidate_count = parse_positive_int(request, "candidate_count", 32);

        std::vector<ProcessInvocationSpec> candidates;
        candidates.reserve(static_cast<std::size_t>(candidate_count));
        std::set<std::string> ids;
        for (int index = 0; index < candidate_count; ++index) {
            const std::string prefix = "candidate." + std::to_string(index) + ".";
            const auto kind = metadata_value(request, prefix + "kind");
            if (kind != "PROCESS") throw std::runtime_error("LCR1 supports only PROCESS invocation candidates");
            ProcessInvocationSpec candidate;
            candidate.candidate_id = metadata_value(request, prefix + "id");
            validate_wire_text(candidate.candidate_id, "candidate id");
            if (!ids.insert(candidate.candidate_id).second) throw std::runtime_error("candidate ids must be unique within Work");
            candidate.executable = metadata_value(request, prefix + "executable");
            validate_wire_text(candidate.executable.string(), "process executable");
            candidate.target_identity = optional_metadata(request, prefix + "target_identity");
            validate_wire_text(candidate.target_identity, "target identity", true);
            const int arg_count = parse_nonnegative_int(request, prefix + "arg_count", 128);
            for (int arg = 0; arg < arg_count; ++arg) {
                const auto value = metadata_value(request, prefix + "arg." + std::to_string(arg));
                validate_wire_text(value, "process argument", true);
                candidate.arguments.push_back(value);
            }
            candidates.push_back(std::move(candidate));
        }

        const auto submitted_at = now_ms();
        const auto eligible_at = optional_nonnegative_i64(request, "eligible_at_ms").value_or(submitted_at);
        const auto deadline = optional_nonnegative_i64(request, "deadline_ms");
        const auto attempt_timeout = optional_nonnegative_i64(request, "attempt_timeout_ms");
        const auto id = make_work_id();
        const auto work_dir = data_dir_ / "work" / id;
        const auto input_path = work_dir / "input.bin";
        const auto result_path = work_dir / "result.bin";
        write_binary_atomic(input_path, request.payload);
        store_.submit(WorkRecord{
            id,
            "QUEUED",
            urgency,
            submitted_at,
            eligible_at,
            eligible_at,
            deadline,
            attempt_timeout,
            max_attempts,
            retry_delay_ms,
            retry_safety,
            0,
            input_path,
            result_path,
            false,
            false,
            {},
            {},
            {},
            std::move(candidates),
        });
        wake_.notify_one();
        auto out = response(MessageType::SubmitResponse, request);
        out.metadata["work_id"] = id;
        return out;
    }

    Frame status(const Frame& request) {
        const auto id = metadata_value(request, "work_id");
        const auto work = store_.find(id);
        if (!work) return error_response(request, "WORK_NOT_FOUND", "unknown WorkId");
        auto out = response(MessageType::StatusResponse, request);
        out.metadata["state"] = work->state;
        out.metadata["attempt_count"] = std::to_string(work->attempt_count);
        out.metadata["selected_candidate_id"] = work->selected_candidate_id;
        out.metadata["selected_target_identity"] = work->selected_target_identity;
        out.metadata["technical_failure"] = work->technical_failure;
        return out;
    }

    Frame result(const Frame& request) {
        const auto id = metadata_value(request, "work_id");
        const auto work = store_.find(id);
        if (!work) return error_response(request, "WORK_NOT_FOUND", "unknown WorkId");
        auto out = response(MessageType::ResultResponse, request);
        out.metadata["state"] = work->state;
        const bool available = work->state == "SUCCEEDED" && !work->acknowledged && fs::exists(work->result_path);
        out.metadata["available"] = available ? "true" : "false";
        if (available) out.payload = read_binary_bounded(work->result_path);
        return out;
    }

    Frame acknowledge(const Frame& request) {
        const auto id = metadata_value(request, "work_id");
        const auto work = store_.find(id);
        if (!work) return error_response(request, "WORK_NOT_FOUND", "unknown WorkId");
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
        if (state.empty()) return error_response(request, "WORK_NOT_FOUND", "unknown WorkId");
        wake_.notify_one();
        auto out = response(MessageType::CancelResponse, request);
        out.metadata["state"] = state;
        return out;
    }

    fs::path data_dir_;
    LocalIpcServer local_ipc_;
    WorkStore store_;
    ProcessExecutor process_executor_;
    int max_concurrent_;
    std::atomic<int> active_attempts_{0};
    std::atomic_bool stop_{false};
    std::thread scheduler_;
    std::vector<std::future<void>> attempt_tasks_;
    std::mutex wake_mutex_;
    std::condition_variable wake_;
};

struct Options {
    fs::path data_dir;
    fs::path endpoint;
    int max_concurrent{2};
};

Options parse_options(int argc, char** argv) {
    Options options;
    for (int i = 1; i < argc; ++i) {
        const std::string argument = argv[i];
        if (argument == "--data-dir" && i + 1 < argc) options.data_dir = argv[++i];
        else if (argument == "--endpoint" && i + 1 < argc) options.endpoint = argv[++i];
        else if (argument == "--max-concurrent" && i + 1 < argc) options.max_concurrent = std::stoi(argv[++i]);
        else throw std::runtime_error("usage: madre-kernel --data-dir <path> --endpoint <local-endpoint> [--max-concurrent N]");
    }
    if (options.data_dir.empty() || options.endpoint.empty()) {
        throw std::runtime_error("--data-dir and --endpoint are required");
    }
    if (options.max_concurrent < 1 || options.max_concurrent > 64) {
        throw std::runtime_error("--max-concurrent must be between 1 and 64");
    }
    return options;
}

}  // namespace
}  // namespace madre::kernel

int main(int argc, char** argv) {
    using namespace madre::kernel;
    try {
        std::signal(SIGINT, on_signal);
        std::signal(SIGTERM, on_signal);
#ifndef _WIN32
        std::signal(SIGPIPE, SIG_IGN);
#else
        ::SetConsoleCtrlHandler(on_console_control, TRUE);
#endif
        const auto options = parse_options(argc, argv);
        Kernel kernel(options.data_dir, options.endpoint, options.max_concurrent);
        kernel.run();
        return 0;
    } catch (const std::exception& ex) {
        std::cerr << "madre-kernel: " << ex.what() << '\n';
        return 1;
    }
}
