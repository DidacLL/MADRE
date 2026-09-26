#include "http_executor.hpp"
#include "local_ipc.hpp"
#include "process_executor.hpp"
#include "protocol.hpp"
#include "store.hpp"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cctype>
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
#include <system_error>
#include <thread>
#include <variant>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#endif

namespace fs = std::filesystem;
using namespace std::chrono_literals;

namespace madre::kernel {
namespace {
std::atomic_bool g_stop{false};
constexpr std::size_t kMaxConcurrentLocalClients = 16;

void on_signal(int) { g_stop.store(true); }
#ifdef _WIN32
BOOL WINAPI on_console_control(DWORD event) {
    if (event == CTRL_C_EVENT || event == CTRL_BREAK_EVENT || event == CTRL_CLOSE_EVENT || event == CTRL_SHUTDOWN_EVENT) {
        g_stop.store(true); return TRUE;
    }
    return FALSE;
}
#endif

std::int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
}

std::string make_work_id() {
    std::random_device rd; std::mt19937_64 generator(rd()); std::uniform_int_distribution<std::uint64_t> distribution;
    std::ostringstream out; out << std::hex << std::setfill('0') << std::setw(16) << distribution(generator) << std::setw(16) << distribution(generator); return out.str();
}

void require_payload_size(std::uintmax_t size, std::string_view description) {
    if (size > kMaxBoundedPayloadBytes) throw std::runtime_error(std::string(description) + " exceeds the 1 MiB bounded physical payload limit");
}

void write_binary_atomic(const fs::path& path, const std::vector<std::uint8_t>& data) {
    require_payload_size(data.size(), "file-backed payload"); fs::create_directories(path.parent_path()); const auto temporary = path.string() + ".tmp";
    { std::ofstream stream(temporary, std::ios::binary | std::ios::trunc); if (!stream) throw std::runtime_error("open payload file for write"); if (!data.empty()) stream.write(reinterpret_cast<const char*>(data.data()), static_cast<std::streamsize>(data.size())); stream.flush(); if (!stream) throw std::runtime_error("write payload file"); }
    std::error_code error; fs::remove(path, error); fs::rename(temporary, path);
}

std::vector<std::uint8_t> read_binary_bounded(const fs::path& path) {
    std::error_code error; const auto size = fs::file_size(path, error); if (error) throw std::runtime_error("inspect payload file size"); require_payload_size(size, "file-backed payload");
    std::ifstream stream(path, std::ios::binary); if (!stream) throw std::runtime_error("open payload file for read"); std::vector<std::uint8_t> data(static_cast<std::size_t>(size));
    if (!data.empty()) { stream.read(reinterpret_cast<char*>(data.data()), static_cast<std::streamsize>(data.size())); if (!stream) throw std::runtime_error("read payload file"); }
    return data;
}

bool remove_retained_file(const fs::path& path) {
    std::error_code error;
    fs::remove(path, error);
    return !error || error == std::errc::no_such_file_or_directory;
}

Frame response(MessageType type, const Frame& request) { return Frame{type, request.correlation_id, {}, {}}; }
Frame error_response(const Frame& request, std::string code, std::string message) { Frame out{MessageType::Error, request.correlation_id, {}, {}}; out.metadata.emplace("code", std::move(code)); out.metadata.emplace("message", std::move(message)); return out; }
std::string optional_metadata(const Frame& frame, const std::string& key) { const auto it = frame.metadata.find(key); return it == frame.metadata.end() ? std::string{} : it->second; }

std::int64_t parse_i64(const std::string& value, const char* field) {
    try { std::size_t used = 0; const auto parsed = std::stoll(value, &used); if (used != value.size()) throw std::invalid_argument("trailing"); return parsed; }
    catch (const std::exception&) { throw std::runtime_error(std::string("invalid integer metadata field: ") + field); }
}

std::optional<std::int64_t> optional_nonnegative_i64(const Frame& frame, const std::string& key) {
    const auto value = optional_metadata(frame, key); if (value.empty()) return std::nullopt; const auto parsed = parse_i64(value, key.c_str()); if (parsed < 0) throw std::runtime_error(key + " must be >= 0"); return parsed;
}

int parse_positive_int(const Frame& frame, const std::string& key, int maximum = std::numeric_limits<int>::max()) {
    const auto parsed = parse_i64(metadata_value(frame, key), key.c_str()); if (parsed < 1 || parsed > maximum) throw std::runtime_error(key + " must be within the supported positive range"); return static_cast<int>(parsed);
}

int parse_nonnegative_int(const Frame& frame, const std::string& key, int maximum) {
    const auto parsed = parse_i64(metadata_value(frame, key), key.c_str()); if (parsed < 0 || parsed > maximum) throw std::runtime_error(key + " is outside the supported range"); return static_cast<int>(parsed);
}

void validate_urgency(std::string_view value) { if (value != "INTERACTIVE" && value != "NORMAL" && value != "BACKGROUND") throw std::runtime_error("invalid urgency"); }
void validate_retry_safety(std::string_view value) { if (value != "NEVER" && value != "DEFINITE_FAILURES" && value != "INCLUDING_UNKNOWN_COMPLETION") throw std::runtime_error("invalid retry_safety"); }
void validate_wire_text(std::string_view value, const char* field, bool allow_empty = false) { if ((!allow_empty && value.empty()) || value.find_first_of("\r\n") != std::string_view::npos) throw std::runtime_error(std::string("invalid ") + field); }

bool header_name_valid(std::string_view value) {
    if (value.empty()) return false;
    for (unsigned char c : value) {
        if (c <= 32U || c >= 127U) return false;
        switch (static_cast<char>(c)) {
            case '(' : case ')' : case '<' : case '>' : case '@' : case ',' : case ';' : case ':' :
            case '\\' : case '"' : case '/' : case '[' : case ']' : case '?' : case '=' : case '{' : case '}' :
                return false;
            default:
                break;
        }
    }
    return true;
}

void validate_http_uri(const std::string& uri) {
    validate_wire_text(uri, "HTTP URI");
    std::size_t authority_start = 0;
    if (uri.rfind("http://", 0) == 0) authority_start = 7;
    else if (uri.rfind("https://", 0) == 0) authority_start = 8;
    else throw std::runtime_error("HTTP URI scheme must be http or https");
    const auto authority_end = uri.find_first_of("/?#", authority_start);
    const auto authority = uri.substr(authority_start, authority_end == std::string::npos ? std::string::npos : authority_end - authority_start);
    if (authority.empty()) throw std::runtime_error("HTTP URI must contain an authority");
    if (authority.find('@') != std::string::npos) throw std::runtime_error("HTTP URI user-info is rejected; use late-bound headers for credentials");
}

std::string single_line_failure(std::string failure, const std::string& stderr_text) {
    if (!stderr_text.empty()) { std::string compact = stderr_text; std::replace(compact.begin(), compact.end(), '\r', ' '); std::replace(compact.begin(), compact.end(), '\n', ' '); if (compact.size() > 512) compact.resize(512); failure += ": stderr=" + compact; }
    return failure;
}

std::vector<std::uint8_t> payload_slice(const Frame& request, std::size_t offset, std::size_t length) {
    if (offset > request.payload.size() || length > request.payload.size() - offset) throw std::runtime_error("candidate payload slice exceeds submitted frame payload");
    return std::vector<std::uint8_t>(request.payload.begin() + static_cast<std::ptrdiff_t>(offset), request.payload.begin() + static_cast<std::ptrdiff_t>(offset + length));
}

class Kernel {
public:
    Kernel(fs::path data_dir, fs::path endpoint, int max_concurrent)
        : data_dir_(std::move(data_dir)), local_ipc_(std::move(endpoint), data_dir_), store_(data_dir_ / "kernel.db"), max_concurrent_(max_concurrent) {
        fs::create_directories(data_dir_ / "work"); store_.recover_interrupted(now_ms());
    }
    ~Kernel() {
        stop_.store(true); wake_.notify_all();
        if (scheduler_.joinable()) scheduler_.join();
        for (auto& task : attempt_tasks_) if (task.valid()) task.wait();
        for (auto& task : client_tasks_) if (task.valid()) task.wait();
    }
    void run() {
        scheduler_ = std::thread([this] { scheduler_loop(); });
        while (!g_stop.load() && !stop_.load()) {
            std::erase_if(client_tasks_, [](std::future<void>& task) { return task.wait_for(0ms) == std::future_status::ready; });
            if (client_tasks_.size() >= kMaxConcurrentLocalClients) {
                std::this_thread::sleep_for(10ms);
                continue;
            }
            const int fd = local_ipc_.accept_for(100ms);
            if (fd < 0) continue;
            try {
                client_tasks_.push_back(std::async(std::launch::async, [this, fd] {
                    serve_connection(fd);
                    close_local_ipc(fd);
                }));
            } catch (...) {
                close_local_ipc(fd);
                throw;
            }
        }
        stop_.store(true); wake_.notify_all();
    }

private:
    std::optional<ConcretePhysicalInvocationSpec> select_candidate(const WorkRecord& work) const {
        for (const auto& candidate : work.candidates) {
            if (const auto* process = std::get_if<ProcessInvocationSpec>(&candidate)) {
                if (process_executor_.executable_available(process->executable)) return candidate;
            } else if (http_executor_.dispatchable(std::get<HttpInvocationSpec>(candidate))) {
                return candidate;
            }
        }
        return std::nullopt;
    }

    void scheduler_loop() {
        while (!stop_.load()) {
            std::erase_if(attempt_tasks_, [](std::future<void>& task) { return task.wait_for(0ms) == std::future_status::ready; });
            if (active_attempts_.load() >= max_concurrent_) { std::unique_lock lock(wake_mutex_); wake_.wait_for(lock, 10ms, [this] { return stop_.load(); }); continue; }
            const auto scheduler_now = now_ms(); store_.expire_queued_deadlines(scheduler_now); auto work = store_.next_eligible(scheduler_now);
            if (!work) { std::unique_lock lock(wake_mutex_); wake_.wait_for(lock, 20ms, [this] { return stop_.load(); }); continue; }
            if (work->deadline_ms && scheduler_now >= *work->deadline_ms) { store_.fail_queued(work->id, "DEADLINE_EXPIRED: Work deadline expired before dispatch"); continue; }
            const auto selected = select_candidate(*work);
            if (!selected) { store_.fail_queued(work->id, "NO_DISPATCHABLE_CANDIDATE: none of the supplied concrete invocation candidates is physically dispatchable"); continue; }
            const auto started_at = now_ms(); const int attempt_number = store_.begin_attempt(work->id, *selected, started_at); if (attempt_number == 0) continue;
            active_attempts_.fetch_add(1);
            attempt_tasks_.push_back(std::async(std::launch::async, [this, work = *work, candidate = *selected, attempt_number, started_at]() {
                execute_attempt(work, candidate, attempt_number, started_at); active_attempts_.fetch_sub(1); wake_.notify_all();
            }));
        }
    }

    std::pair<std::optional<std::int64_t>, bool> stop_time_for(const WorkRecord& work, std::int64_t started_at) const {
        std::optional<std::int64_t> stop_at; bool timeout_wins = false;
        if (work.attempt_timeout_ms) { stop_at = started_at + *work.attempt_timeout_ms; timeout_wins = true; }
        if (work.deadline_ms && (!stop_at || *work.deadline_ms < *stop_at)) { stop_at = *work.deadline_ms; timeout_wins = false; }
        return {stop_at, timeout_wins};
    }

    void execute_attempt(const WorkRecord& work, const ConcretePhysicalInvocationSpec& candidate, int attempt_number, std::int64_t started_at) {
        try {
            const auto [stop_at, timeout_wins] = stop_time_for(work, started_at);
            const auto input = read_binary_bounded(payload_path(candidate));
            if (const auto* process = std::get_if<ProcessInvocationSpec>(&candidate)) {
                const auto outcome = process_executor_.execute(*process, input, [this, &work] { return store_.cancel_requested(work.id); }, [this] { return stop_.load(); }, stop_at, timeout_wins);
                switch (outcome.kind) {
                    case ProcessOutcomeKind::Stopped: return;
                    case ProcessOutcomeKind::Cancelled: store_.finish_cancelled(work.id, attempt_number, "PROCESS_CANCELLED", outcome.exit_code, std::nullopt, now_ms()); return;
                    case ProcessOutcomeKind::TimedOut: store_.finish_definite_failure(work.id, attempt_number, "TIMED_OUT", single_line_failure(outcome.technical_failure, outcome.stderr_text), outcome.exit_code, std::nullopt, now_ms()); return;
                    case ProcessOutcomeKind::TechnicalFailure: store_.finish_definite_failure(work.id, attempt_number, "FAILED", single_line_failure(outcome.technical_failure, outcome.stderr_text), outcome.exit_code, std::nullopt, now_ms()); return;
                    case ProcessOutcomeKind::Succeeded: break;
                }
                if (store_.cancel_requested(work.id)) { store_.finish_cancelled(work.id, attempt_number, "PROCESS_CANCELLED_AFTER_EXIT_BEFORE_RESULT_RETENTION", outcome.exit_code, std::nullopt, now_ms()); return; }
                write_binary_atomic(work.result_path, outcome.stdout_payload); store_.finish_success(work.id, attempt_number, outcome.exit_code, std::nullopt, now_ms()); return;
            }

            const auto& http = std::get<HttpInvocationSpec>(candidate);
            const auto outcome = http_executor_.execute(http, input, [this, &work] { return store_.cancel_requested(work.id); }, [this] { return stop_.load(); }, stop_at, timeout_wins);
            switch (outcome.kind) {
                case HttpOutcomeKind::Stopped: return;
                case HttpOutcomeKind::CancelledBeforeSubmission:
                    store_.finish_cancelled(work.id, attempt_number, outcome.technical_failure, std::nullopt, outcome.http_status, now_ms()); return;
                case HttpOutcomeKind::UnknownCompletion:
                    store_.finish_unknown_completion(work.id, attempt_number, outcome.technical_failure, outcome.http_status, now_ms()); return;
                case HttpOutcomeKind::DefiniteFailure: {
                    const bool timed_out = outcome.technical_failure.rfind("ATTEMPT_TIMEOUT", 0) == 0 || outcome.technical_failure.rfind("DEADLINE_EXPIRED_DURING_ATTEMPT", 0) == 0;
                    store_.finish_definite_failure(work.id, attempt_number, timed_out ? "TIMED_OUT" : "FAILED", outcome.technical_failure, std::nullopt, outcome.http_status, now_ms()); return;
                }
                case HttpOutcomeKind::Succeeded:
                    write_binary_atomic(work.result_path, outcome.response_body); store_.finish_success(work.id, attempt_number, std::nullopt, outcome.http_status, now_ms()); return;
            }
        } catch (const std::exception& ex) {
            if (stop_.load()) return;
            store_.finish_definite_failure(work.id, attempt_number, "FAILED", std::string("PHYSICAL_EXECUTION_FAILURE_BEFORE_CONFIRMED_RESULT: ") + ex.what(), std::nullopt, std::nullopt, now_ms());
        }
    }

    void serve_connection(int fd) {
        Frame hello_request{};
        try {
            hello_request = read_frame(fd); if (hello_request.type != MessageType::Hello) { write_frame(fd, error_response(hello_request, "HELLO_REQUIRED", "HELLO must be the first frame")); return; }
            const auto hello_response = hello(hello_request); write_frame(fd, hello_response); if (hello_response.type == MessageType::Error) return;
            const auto request = read_frame(fd); write_frame(fd, handle(request));
        } catch (const FramingError& ex) { try { write_frame(fd, error_response(hello_request, "FRAMING_ERROR", ex.what())); } catch (...) {} }
        catch (const std::exception& ex) { try { write_frame(fd, error_response(hello_request, "PROTOCOL_OR_REQUEST_ERROR", ex.what())); } catch (...) {} }
    }

    Frame handle(const Frame& request) {
        switch (request.type) {
            case MessageType::Submit: return submit(request);
            case MessageType::Status: return status(request);
            case MessageType::Result: return result(request);
            case MessageType::Release: return release(request);
            case MessageType::Cancel: return cancel(request);
            default: return error_response(request, "UNKNOWN_MESSAGE", "unsupported Kernel command");
        }
    }

    Frame hello(const Frame& request) {
        const auto client_min = std::stoi(metadata_value(request, "min_kernel_protocol_version")); const auto client_max = std::stoi(metadata_value(request, "max_kernel_protocol_version"));
        const auto overlap_min = std::max(client_min, kMinKernelProtocolVersion); const auto overlap_max = std::min(client_max, kMaxKernelProtocolVersion);
        if (client_min > client_max || overlap_min > overlap_max) return error_response(request, "VERSION_MISMATCH", "no compatible Kernel protocol/API version");
        auto out = response(MessageType::HelloResponse, request); out.metadata["kernel_protocol_version"] = std::to_string(overlap_max); return out;
    }

    Frame submit(const Frame& request) {
        require_payload_size(request.payload.size(), "submitted candidate payload material");
        const auto urgency = metadata_value(request, "urgency"); validate_urgency(urgency);
        const auto retry_safety = metadata_value(request, "retry_safety"); validate_retry_safety(retry_safety);
        const auto max_attempts = parse_positive_int(request, "max_attempts", 1000); const auto retry_delay_ms = parse_i64(metadata_value(request, "retry_delay_ms"), "retry_delay_ms"); if (retry_delay_ms < 0) throw std::runtime_error("retry_delay_ms must be >= 0");
        const int candidate_count = parse_positive_int(request, "candidate_count", 32);
        const auto submitted_at = now_ms(); const auto eligible_at = optional_nonnegative_i64(request, "eligible_at_ms").value_or(submitted_at); const auto deadline = optional_nonnegative_i64(request, "deadline_ms"); const auto attempt_timeout = optional_nonnegative_i64(request, "attempt_timeout_ms");
        const auto id = make_work_id(); const auto work_dir = data_dir_ / "work" / id; const auto result_path = work_dir / "result.bin"; fs::create_directories(work_dir);
        std::vector<ConcretePhysicalInvocationSpec> candidates; candidates.reserve(static_cast<std::size_t>(candidate_count)); std::set<std::string> ids; std::size_t expected_offset = 0;
        try {
            for (int index = 0; index < candidate_count; ++index) {
                const std::string prefix = "candidate." + std::to_string(index) + "."; const auto kind = metadata_value(request, prefix + "kind"); const auto candidate_id = metadata_value(request, prefix + "id"); validate_wire_text(candidate_id, "candidate id");
                if (!ids.insert(candidate_id).second) throw std::runtime_error("candidate ids must be unique within Work");
                const auto target = optional_metadata(request, prefix + "target_identity"); validate_wire_text(target, "target identity", true);
                const auto offset = parse_nonnegative_int(request, prefix + "payload_offset", static_cast<int>(kMaxBoundedPayloadBytes)); const auto length = parse_nonnegative_int(request, prefix + "payload_length", static_cast<int>(kMaxBoundedPayloadBytes));
                if (static_cast<std::size_t>(offset) != expected_offset) throw std::runtime_error("candidate payloads must form one bounded ordered partition of the submission payload");
                expected_offset += static_cast<std::size_t>(length); if (expected_offset > request.payload.size()) throw std::runtime_error("candidate payload partition exceeds submission payload");
                const auto candidate_payload_path = work_dir / ("candidate-" + std::to_string(index) + ".bin"); write_binary_atomic(candidate_payload_path, payload_slice(request, static_cast<std::size_t>(offset), static_cast<std::size_t>(length)));
                if (kind == "PROCESS") {
                    ProcessInvocationSpec candidate; candidate.candidate_id = candidate_id; candidate.target_identity = target; candidate.payload_path = candidate_payload_path; candidate.executable = metadata_value(request, prefix + "executable"); validate_wire_text(candidate.executable.string(), "process executable");
                    const int arg_count = parse_nonnegative_int(request, prefix + "arg_count", 128); for (int arg = 0; arg < arg_count; ++arg) { const auto value = metadata_value(request, prefix + "arg." + std::to_string(arg)); validate_wire_text(value, "process argument", true); candidate.arguments.push_back(value); }
                    candidates.emplace_back(std::move(candidate));
                } else if (kind == "HTTP") {
                    HttpInvocationSpec candidate; candidate.candidate_id = candidate_id; candidate.target_identity = target; candidate.payload_path = candidate_payload_path; candidate.uri = metadata_value(request, prefix + "uri"); validate_http_uri(candidate.uri);
                    const int header_count = parse_nonnegative_int(request, prefix + "header_count", 64); std::set<std::string> header_names;
                    for (int header_index = 0; header_index < header_count; ++header_index) {
                        const std::string hp = prefix + "header." + std::to_string(header_index) + "."; HttpHeaderSpec header; header.name = metadata_value(request, hp + "name"); if (!header_name_valid(header.name)) throw std::runtime_error("invalid HTTP header name");
                        std::string folded = header.name; std::transform(folded.begin(), folded.end(), folded.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); }); if (!header_names.insert(folded).second) throw std::runtime_error("duplicate HTTP header name");
                        const auto source = metadata_value(request, hp + "source");
                        if (source == "LITERAL") { header.source = HttpHeaderSource::Literal; header.literal_value = metadata_value(request, hp + "value"); validate_wire_text(header.literal_value, "literal HTTP header value", true); }
                        else if (source == "ENVIRONMENT") { header.source = HttpHeaderSource::Environment; header.environment_variable = metadata_value(request, hp + "environment_variable"); header.prefix = metadata_value(request, hp + "prefix"); header.suffix = metadata_value(request, hp + "suffix"); validate_wire_text(header.environment_variable, "environment variable"); validate_wire_text(header.prefix, "HTTP header prefix", true); validate_wire_text(header.suffix, "HTTP header suffix", true); }
                        else throw std::runtime_error("invalid HTTP header value source");
                        candidate.headers.push_back(std::move(header));
                    }
                    candidates.emplace_back(std::move(candidate));
                } else throw std::runtime_error("unsupported concrete invocation kind");
            }
            if (expected_offset != request.payload.size()) throw std::runtime_error("candidate payload partition does not consume the complete submission payload");
            store_.submit(WorkRecord{id, "QUEUED", urgency, submitted_at, eligible_at, eligible_at, deadline, attempt_timeout, max_attempts, retry_delay_ms, retry_safety, 0, result_path, false, false, {}, std::move(candidates), std::nullopt});
        } catch (...) { std::error_code error; fs::remove_all(work_dir, error); throw; }
        wake_.notify_one(); auto out = response(MessageType::SubmitResponse, request); out.metadata["work_id"] = id; return out;
    }

    Frame status(const Frame& request) {
        const auto id = metadata_value(request, "work_id"); const auto work = store_.find(id); if (!work) return error_response(request, "WORK_NOT_FOUND", "unknown WorkId");
        auto out = response(MessageType::StatusResponse, request); out.metadata["state"] = work->state; out.metadata["attempt_count"] = std::to_string(work->attempt_count); out.metadata["technical_failure"] = work->technical_failure; out.metadata["payload_released"] = work->released ? "true" : "false"; out.metadata["has_latest_attempt"] = work->latest_attempt ? "true" : "false";
        if (work->latest_attempt) {
            const auto& attempt = *work->latest_attempt; out.metadata["attempt.number"] = std::to_string(attempt.attempt_number); out.metadata["attempt.invocation_id"] = attempt.candidate_id; out.metadata["attempt.kind"] = attempt.kind; out.metadata["attempt.target_identity"] = attempt.target_identity; out.metadata["attempt.state"] = attempt.state; out.metadata["attempt.started_at_ms"] = std::to_string(attempt.started_at_ms); out.metadata["attempt.ended_at_ms"] = attempt.ended_at_ms ? std::to_string(*attempt.ended_at_ms) : ""; out.metadata["attempt.exit_code"] = attempt.exit_code ? std::to_string(*attempt.exit_code) : ""; out.metadata["attempt.http_status"] = attempt.http_status ? std::to_string(*attempt.http_status) : ""; out.metadata["attempt.technical_failure"] = attempt.technical_failure;
        }
        return out;
    }

    Frame result(const Frame& request) {
        const auto id = metadata_value(request, "work_id"); const auto work = store_.find(id); if (!work) return error_response(request, "WORK_NOT_FOUND", "unknown WorkId"); auto out = response(MessageType::ResultResponse, request);
        const bool available = work->state == "SUCCEEDED" && !work->released && fs::exists(work->result_path); out.metadata["state"] = work->state; out.metadata["available"] = available ? "true" : "false"; if (available) out.payload = read_binary_bounded(work->result_path); return out;
    }

    Frame release(const Frame& request) {
        const auto id = metadata_value(request, "work_id"); const auto work = store_.find(id); if (!work) return error_response(request, "WORK_NOT_FOUND", "unknown WorkId");
        const bool terminal = work->state == "SUCCEEDED" || work->state == "FAILED" || work->state == "CANCELLED" || work->state == "UNKNOWN_COMPLETION"; if (!terminal) return error_response(request, "WORK_NOT_TERMINAL", "retained payload may be released only after terminal Work");
        if (!work->released) {
            for (const auto& candidate : work->candidates) {
                if (!remove_retained_file(payload_path(candidate))) return error_response(request, "PAYLOAD_DELETE_FAILED", "failed to remove retained candidate payload");
            }
            if (!remove_retained_file(work->result_path)) return error_response(request, "PAYLOAD_DELETE_FAILED", "failed to remove retained result payload");
            if (!store_.release(id)) return error_response(request, "RELEASE_REJECTED", "terminal Work could not be marked released");
        }
        auto out = response(MessageType::ReleaseResponse, request); out.metadata["released"] = "true"; return out;
    }

    Frame cancel(const Frame& request) {
        const auto id = metadata_value(request, "work_id"); const auto state = store_.cancel(id); if (state.empty()) return error_response(request, "WORK_NOT_FOUND", "unknown WorkId"); wake_.notify_one(); auto out = response(MessageType::CancelResponse, request); out.metadata["state"] = state; return out;
    }

    fs::path data_dir_; LocalIpcServer local_ipc_; WorkStore store_; ProcessExecutor process_executor_; HttpExecutor http_executor_; int max_concurrent_;
    std::atomic<int> active_attempts_{0}; std::atomic_bool stop_{false}; std::thread scheduler_; std::vector<std::future<void>> attempt_tasks_; std::vector<std::future<void>> client_tasks_; std::mutex wake_mutex_; std::condition_variable wake_;
};

struct Options { fs::path data_dir; fs::path endpoint; int max_concurrent{2}; };
Options parse_options(int argc, char** argv) {
    Options options;
    for (int i = 1; i < argc; ++i) { const std::string argument = argv[i]; if (argument == "--data-dir" && i + 1 < argc) options.data_dir = argv[++i]; else if (argument == "--endpoint" && i + 1 < argc) options.endpoint = argv[++i]; else if (argument == "--max-concurrent" && i + 1 < argc) options.max_concurrent = std::stoi(argv[++i]); else throw std::runtime_error("usage: madre-kernel --data-dir <path> --endpoint <local-endpoint> [--max-concurrent N]"); }
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
        std::signal(SIGINT, on_signal); std::signal(SIGTERM, on_signal);
#ifndef _WIN32
        std::signal(SIGPIPE, SIG_IGN);
#else
        ::SetConsoleCtrlHandler(on_console_control, TRUE);
#endif
        const auto options = parse_options(argc, argv); Kernel kernel(options.data_dir, options.endpoint, options.max_concurrent); kernel.run(); return 0;
    } catch (const std::exception& ex) { std::cerr << "madre-kernel: " << ex.what() << '\n'; return 1; }
}
