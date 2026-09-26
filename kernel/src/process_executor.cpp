#include "process_executor.hpp"

#include "protocol.hpp"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <stdexcept>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#else
#include <cerrno>
#include <csignal>
#include <spawn.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

extern char** environ;
#endif

namespace fs = std::filesystem;

namespace madre::kernel {
namespace {
using namespace std::chrono_literals;

std::int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::system_clock::now().time_since_epoch())
        .count();
}

std::string bounded_text(const std::vector<std::uint8_t>& bytes) {
    return std::string(bytes.begin(), bytes.end());
}

#ifndef _WIN32
void close_fd(int& fd) noexcept {
    if (fd >= 0) {
        ::close(fd);
        fd = -1;
    }
}

bool path_is_executable(const fs::path& value) {
    return !value.empty() && ::access(value.c_str(), X_OK) == 0;
}

bool search_path_executable(const fs::path& executable) {
    const auto name = executable.string();
    if (name.empty()) return false;
    if (name.find('/') != std::string::npos) return path_is_executable(executable);
    const char* raw_path = std::getenv("PATH");
    if (raw_path == nullptr) return false;
    std::string path(raw_path);
    std::size_t start = 0;
    while (start <= path.size()) {
        const auto end = path.find(':', start);
        const auto segment = path.substr(start, end == std::string::npos ? std::string::npos : end - start);
        fs::path candidate = segment.empty() ? fs::path(".") / executable : fs::path(segment) / executable;
        if (path_is_executable(candidate)) return true;
        if (end == std::string::npos) break;
        start = end + 1;
    }
    return false;
}

std::vector<char*> argv_for(const ProcessInvocationSpec& invocation, std::vector<std::string>& storage) {
    storage.clear();
    storage.reserve(invocation.arguments.size() + 1);
    storage.push_back(invocation.executable.string());
    storage.insert(storage.end(), invocation.arguments.begin(), invocation.arguments.end());
    std::vector<char*> argv;
    argv.reserve(storage.size() + 1);
    for (auto& value : storage) argv.push_back(value.data());
    argv.push_back(nullptr);
    return argv;
}

void read_pipe_bounded(int fd, std::vector<std::uint8_t>& target, std::size_t bound,
                       std::atomic_bool& overflow) {
    std::uint8_t buffer[8192];
    while (true) {
        const auto n = ::read(fd, buffer, sizeof(buffer));
        if (n == 0) break;
        if (n < 0) {
            if (errno == EINTR) continue;
            break;
        }
        const auto amount = static_cast<std::size_t>(n);
        if (target.size() < bound) {
            const auto keep = std::min(amount, bound - target.size());
            target.insert(target.end(), buffer, buffer + keep);
            if (keep < amount) overflow.store(true);
        } else {
            overflow.store(true);
        }
    }
    ::close(fd);
}

void write_pipe_all(int fd, const std::vector<std::uint8_t>& input) {
    std::size_t offset = 0;
    while (offset < input.size()) {
        const auto n = ::write(fd, input.data() + offset, input.size() - offset);
        if (n < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (n == 0) break;
        offset += static_cast<std::size_t>(n);
    }
    ::close(fd);
}
#else
std::wstring utf8_to_wide(const std::string& value) {
    if (value.empty()) return {};
    const int count = ::MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(),
                                            static_cast<int>(value.size()), nullptr, 0);
    if (count <= 0) throw std::runtime_error("process argument is not valid UTF-8");
    std::wstring result(static_cast<std::size_t>(count), L'\0');
    if (::MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(),
                              static_cast<int>(value.size()), result.data(), count) != count) {
        throw std::runtime_error("convert process argument to UTF-16 failed");
    }
    return result;
}

std::wstring quote_windows_argument(const std::wstring& value) {
    if (value.empty()) return L"\"\"";
    if (value.find_first_of(L" \t\"") == std::wstring::npos) return value;
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

std::wstring command_line_for(const ProcessInvocationSpec& invocation) {
    std::wstring result = quote_windows_argument(invocation.executable.wstring());
    for (const auto& argument : invocation.arguments) {
        result.push_back(L' ');
        result += quote_windows_argument(utf8_to_wide(argument));
    }
    return result;
}

bool search_path_executable(const fs::path& executable) {
    if (executable.empty()) return false;
    const auto wide = executable.wstring();
    if (wide.find(L'\\') != std::wstring::npos || wide.find(L'/') != std::wstring::npos) {
        const auto attrs = ::GetFileAttributesW(wide.c_str());
        return attrs != INVALID_FILE_ATTRIBUTES && (attrs & FILE_ATTRIBUTE_DIRECTORY) == 0;
    }
    DWORD needed = ::SearchPathW(nullptr, wide.c_str(), nullptr, 0, nullptr, nullptr);
    if (needed == 0) return false;
    std::wstring buffer(static_cast<std::size_t>(needed), L'\0');
    return ::SearchPathW(nullptr, wide.c_str(), nullptr, needed, buffer.data(), nullptr) != 0;
}

void read_handle_bounded(HANDLE handle, std::vector<std::uint8_t>& target, std::size_t bound,
                         std::atomic_bool& overflow) {
    std::uint8_t buffer[8192];
    while (true) {
        DWORD read = 0;
        if (!::ReadFile(handle, buffer, sizeof(buffer), &read, nullptr) || read == 0) break;
        const auto amount = static_cast<std::size_t>(read);
        if (target.size() < bound) {
            const auto keep = std::min(amount, bound - target.size());
            target.insert(target.end(), buffer, buffer + keep);
            if (keep < amount) overflow.store(true);
        } else {
            overflow.store(true);
        }
    }
    ::CloseHandle(handle);
}

void write_handle_all(HANDLE handle, const std::vector<std::uint8_t>& input) {
    std::size_t offset = 0;
    while (offset < input.size()) {
        DWORD written = 0;
        const auto remaining = std::min<std::size_t>(input.size() - offset, 64U * 1024U);
        if (!::WriteFile(handle, input.data() + offset, static_cast<DWORD>(remaining), &written, nullptr) || written == 0) break;
        offset += static_cast<std::size_t>(written);
    }
    ::CloseHandle(handle);
}
#endif

}  // namespace

bool ProcessExecutor::executable_available(const fs::path& executable) const {
    return search_path_executable(executable);
}

ProcessOutcome ProcessExecutor::execute(
    const ProcessInvocationSpec& invocation,
    const std::vector<std::uint8_t>& stdin_payload,
    const std::function<bool()>& cancellation_requested,
    const std::function<bool()>& kernel_stopping,
    std::optional<std::int64_t> stop_at_ms,
    bool timeout_wins) const {
    if (stdin_payload.size() > kMaxBoundedPayloadBytes) {
        return {ProcessOutcomeKind::TechnicalFailure, {}, {}, "PROCESS_INPUT_TOO_LARGE", std::nullopt};
    }

#ifndef _WIN32
    int stdin_pipe[2]{-1, -1};
    int stdout_pipe[2]{-1, -1};
    int stderr_pipe[2]{-1, -1};
    if (::pipe(stdin_pipe) != 0 || ::pipe(stdout_pipe) != 0 || ::pipe(stderr_pipe) != 0) {
        close_fd(stdin_pipe[0]); close_fd(stdin_pipe[1]);
        close_fd(stdout_pipe[0]); close_fd(stdout_pipe[1]);
        close_fd(stderr_pipe[0]); close_fd(stderr_pipe[1]);
        return {ProcessOutcomeKind::TechnicalFailure, {}, {}, "PROCESS_PIPE_CREATION_FAILED", std::nullopt};
    }

    std::vector<std::string> argv_storage;
    auto argv = argv_for(invocation, argv_storage);
    posix_spawn_file_actions_t actions{};
    if (::posix_spawn_file_actions_init(&actions) != 0) {
        close_fd(stdin_pipe[0]); close_fd(stdin_pipe[1]);
        close_fd(stdout_pipe[0]); close_fd(stdout_pipe[1]);
        close_fd(stderr_pipe[0]); close_fd(stderr_pipe[1]);
        return {ProcessOutcomeKind::TechnicalFailure, {}, {}, "PROCESS_SPAWN_ACTIONS_FAILED", std::nullopt};
    }
    const int action_rc =
        ::posix_spawn_file_actions_adddup2(&actions, stdin_pipe[0], STDIN_FILENO) ||
        ::posix_spawn_file_actions_adddup2(&actions, stdout_pipe[1], STDOUT_FILENO) ||
        ::posix_spawn_file_actions_adddup2(&actions, stderr_pipe[1], STDERR_FILENO) ||
        ::posix_spawn_file_actions_addclose(&actions, stdin_pipe[0]) ||
        ::posix_spawn_file_actions_addclose(&actions, stdin_pipe[1]) ||
        ::posix_spawn_file_actions_addclose(&actions, stdout_pipe[0]) ||
        ::posix_spawn_file_actions_addclose(&actions, stdout_pipe[1]) ||
        ::posix_spawn_file_actions_addclose(&actions, stderr_pipe[0]) ||
        ::posix_spawn_file_actions_addclose(&actions, stderr_pipe[1]);
    if (action_rc != 0) {
        ::posix_spawn_file_actions_destroy(&actions);
        close_fd(stdin_pipe[0]); close_fd(stdin_pipe[1]);
        close_fd(stdout_pipe[0]); close_fd(stdout_pipe[1]);
        close_fd(stderr_pipe[0]); close_fd(stderr_pipe[1]);
        return {ProcessOutcomeKind::TechnicalFailure, {}, {}, "PROCESS_SPAWN_ACTIONS_FAILED", std::nullopt};
    }

    pid_t pid = -1;
    const auto executable_text = invocation.executable.string();
    const int spawn_rc = executable_text.find('/') == std::string::npos
        ? ::posix_spawnp(&pid, argv[0], &actions, nullptr, argv.data(), environ)
        : ::posix_spawn(&pid, argv[0], &actions, nullptr, argv.data(), environ);
    ::posix_spawn_file_actions_destroy(&actions);
    if (spawn_rc != 0) {
        close_fd(stdin_pipe[0]); close_fd(stdin_pipe[1]);
        close_fd(stdout_pipe[0]); close_fd(stdout_pipe[1]);
        close_fd(stderr_pipe[0]); close_fd(stderr_pipe[1]);
        return {ProcessOutcomeKind::TechnicalFailure, {}, {}, "PROCESS_SPAWN_FAILED: " + std::string(std::strerror(spawn_rc)), std::nullopt};
    }

    close_fd(stdin_pipe[0]);
    close_fd(stdout_pipe[1]);
    close_fd(stderr_pipe[1]);
    std::vector<std::uint8_t> stdout_bytes;
    std::vector<std::uint8_t> stderr_bytes;
    std::atomic_bool stdout_overflow{false};
    std::atomic_bool stderr_overflow{false};
    std::thread writer(write_pipe_all, stdin_pipe[1], std::cref(stdin_payload));
    stdin_pipe[1] = -1;
    std::thread stdout_reader(read_pipe_bounded, stdout_pipe[0], std::ref(stdout_bytes),
                              kMaxBoundedPayloadBytes, std::ref(stdout_overflow));
    stdout_pipe[0] = -1;
    std::thread stderr_reader(read_pipe_bounded, stderr_pipe[0], std::ref(stderr_bytes),
                              kMaxBoundedStderrBytes, std::ref(stderr_overflow));
    stderr_pipe[0] = -1;

    ProcessOutcomeKind forced_kind = ProcessOutcomeKind::TechnicalFailure;
    bool forced = false;
    int status = 0;
    while (true) {
        const auto waited = ::waitpid(pid, &status, WNOHANG);
        if (waited == pid) break;
        if (waited < 0 && errno != EINTR) {
            forced = true;
            forced_kind = ProcessOutcomeKind::TechnicalFailure;
            ::kill(pid, SIGKILL);
            ::waitpid(pid, &status, 0);
            break;
        }
        if (kernel_stopping()) {
            forced = true;
            forced_kind = ProcessOutcomeKind::Stopped;
        } else if (cancellation_requested()) {
            forced = true;
            forced_kind = ProcessOutcomeKind::Cancelled;
        } else if (stop_at_ms && now_ms() >= *stop_at_ms) {
            forced = true;
            forced_kind = ProcessOutcomeKind::TimedOut;
        } else if (stdout_overflow.load()) {
            forced = true;
            forced_kind = ProcessOutcomeKind::TechnicalFailure;
        }
        if (forced) {
            ::kill(pid, SIGKILL);
            while (::waitpid(pid, &status, 0) < 0 && errno == EINTR) {}
            break;
        }
        std::this_thread::sleep_for(10ms);
    }

    writer.join();
    stdout_reader.join();
    stderr_reader.join();
    const auto stderr_text = bounded_text(stderr_bytes);
    if (kernel_stopping() || forced_kind == ProcessOutcomeKind::Stopped) {
        return {ProcessOutcomeKind::Stopped, {}, stderr_text, "KERNEL_STOPPED_DURING_PROCESS_ATTEMPT", std::nullopt};
    }
    if (forced && forced_kind == ProcessOutcomeKind::Cancelled) {
        return {ProcessOutcomeKind::Cancelled, {}, stderr_text, "PROCESS_CANCELLED", std::nullopt};
    }
    if (forced && forced_kind == ProcessOutcomeKind::TimedOut) {
        return {ProcessOutcomeKind::TimedOut, {}, stderr_text,
                timeout_wins ? "ATTEMPT_TIMEOUT" : "DEADLINE_EXPIRED_DURING_ATTEMPT", std::nullopt};
    }
    if (stdout_overflow.load()) {
        return {ProcessOutcomeKind::TechnicalFailure, {}, stderr_text, "PROCESS_STDOUT_TOO_LARGE", std::nullopt};
    }
    if (stderr_overflow.load()) {
        stderr_bytes.resize(std::min(stderr_bytes.size(), kMaxBoundedStderrBytes));
    }
    if (WIFEXITED(status)) {
        const int exit_code = WEXITSTATUS(status);
        if (exit_code == 0) {
            return {ProcessOutcomeKind::Succeeded, std::move(stdout_bytes), stderr_text, {}, exit_code};
        }
        return {ProcessOutcomeKind::TechnicalFailure, {}, stderr_text,
                "PROCESS_EXIT_NONZERO: " + std::to_string(exit_code), exit_code};
    }
    if (WIFSIGNALED(status)) {
        return {ProcessOutcomeKind::TechnicalFailure, {}, stderr_text,
                "PROCESS_TERMINATED_BY_SIGNAL: " + std::to_string(WTERMSIG(status)), std::nullopt};
    }
    return {ProcessOutcomeKind::TechnicalFailure, {}, stderr_text, "PROCESS_TERMINATION_UNKNOWN", std::nullopt};
#else
    SECURITY_ATTRIBUTES attributes{};
    attributes.nLength = sizeof(attributes);
    attributes.bInheritHandle = TRUE;

    HANDLE child_stdin = nullptr, parent_stdin = nullptr;
    HANDLE parent_stdout = nullptr, child_stdout = nullptr;
    HANDLE parent_stderr = nullptr, child_stderr = nullptr;
    if (!::CreatePipe(&child_stdin, &parent_stdin, &attributes, 0) ||
        !::CreatePipe(&parent_stdout, &child_stdout, &attributes, 0) ||
        !::CreatePipe(&parent_stderr, &child_stderr, &attributes, 0)) {
        if (child_stdin) ::CloseHandle(child_stdin); if (parent_stdin) ::CloseHandle(parent_stdin);
        if (parent_stdout) ::CloseHandle(parent_stdout); if (child_stdout) ::CloseHandle(child_stdout);
        if (parent_stderr) ::CloseHandle(parent_stderr); if (child_stderr) ::CloseHandle(child_stderr);
        return {ProcessOutcomeKind::TechnicalFailure, {}, {}, "PROCESS_PIPE_CREATION_FAILED", std::nullopt};
    }
    ::SetHandleInformation(parent_stdin, HANDLE_FLAG_INHERIT, 0);
    ::SetHandleInformation(parent_stdout, HANDLE_FLAG_INHERIT, 0);
    ::SetHandleInformation(parent_stderr, HANDLE_FLAG_INHERIT, 0);

    STARTUPINFOW startup{};
    startup.cb = sizeof(startup);
    startup.dwFlags = STARTF_USESTDHANDLES;
    startup.hStdInput = child_stdin;
    startup.hStdOutput = child_stdout;
    startup.hStdError = child_stderr;
    PROCESS_INFORMATION process{};
    auto command_line = command_line_for(invocation);
    std::vector<wchar_t> mutable_line(command_line.begin(), command_line.end());
    mutable_line.push_back(L'\0');
    const BOOL created = ::CreateProcessW(nullptr, mutable_line.data(), nullptr, nullptr, TRUE,
                                          CREATE_NO_WINDOW, nullptr, nullptr, &startup, &process);
    ::CloseHandle(child_stdin);
    ::CloseHandle(child_stdout);
    ::CloseHandle(child_stderr);
    if (!created) {
        ::CloseHandle(parent_stdin); ::CloseHandle(parent_stdout); ::CloseHandle(parent_stderr);
        return {ProcessOutcomeKind::TechnicalFailure, {}, {},
                "PROCESS_SPAWN_FAILED: Windows error " + std::to_string(::GetLastError()), std::nullopt};
    }
    ::CloseHandle(process.hThread);

    std::vector<std::uint8_t> stdout_bytes;
    std::vector<std::uint8_t> stderr_bytes;
    std::atomic_bool stdout_overflow{false};
    std::atomic_bool stderr_overflow{false};
    std::thread writer(write_handle_all, parent_stdin, std::cref(stdin_payload));
    std::thread stdout_reader(read_handle_bounded, parent_stdout, std::ref(stdout_bytes),
                              kMaxBoundedPayloadBytes, std::ref(stdout_overflow));
    std::thread stderr_reader(read_handle_bounded, parent_stderr, std::ref(stderr_bytes),
                              kMaxBoundedStderrBytes, std::ref(stderr_overflow));

    ProcessOutcomeKind forced_kind = ProcessOutcomeKind::TechnicalFailure;
    bool forced = false;
    while (true) {
        const auto wait = ::WaitForSingleObject(process.hProcess, 10);
        if (wait == WAIT_OBJECT_0) break;
        if (wait == WAIT_FAILED) {
            forced = true;
            forced_kind = ProcessOutcomeKind::TechnicalFailure;
        } else if (kernel_stopping()) {
            forced = true; forced_kind = ProcessOutcomeKind::Stopped;
        } else if (cancellation_requested()) {
            forced = true; forced_kind = ProcessOutcomeKind::Cancelled;
        } else if (stop_at_ms && now_ms() >= *stop_at_ms) {
            forced = true; forced_kind = ProcessOutcomeKind::TimedOut;
        } else if (stdout_overflow.load()) {
            forced = true; forced_kind = ProcessOutcomeKind::TechnicalFailure;
        }
        if (forced) {
            ::TerminateProcess(process.hProcess, 137);
            ::WaitForSingleObject(process.hProcess, INFINITE);
            break;
        }
    }

    DWORD exit_code_raw = 0;
    ::GetExitCodeProcess(process.hProcess, &exit_code_raw);
    ::CloseHandle(process.hProcess);
    writer.join(); stdout_reader.join(); stderr_reader.join();
    const auto stderr_text = bounded_text(stderr_bytes);
    if (kernel_stopping() || forced_kind == ProcessOutcomeKind::Stopped) {
        return {ProcessOutcomeKind::Stopped, {}, stderr_text, "KERNEL_STOPPED_DURING_PROCESS_ATTEMPT", std::nullopt};
    }
    if (forced && forced_kind == ProcessOutcomeKind::Cancelled) {
        return {ProcessOutcomeKind::Cancelled, {}, stderr_text, "PROCESS_CANCELLED", std::nullopt};
    }
    if (forced && forced_kind == ProcessOutcomeKind::TimedOut) {
        return {ProcessOutcomeKind::TimedOut, {}, stderr_text,
                timeout_wins ? "ATTEMPT_TIMEOUT" : "DEADLINE_EXPIRED_DURING_ATTEMPT", std::nullopt};
    }
    if (stdout_overflow.load()) {
        return {ProcessOutcomeKind::TechnicalFailure, {}, stderr_text, "PROCESS_STDOUT_TOO_LARGE", std::nullopt};
    }
    const int exit_code = static_cast<int>(exit_code_raw);
    if (exit_code == 0) {
        return {ProcessOutcomeKind::Succeeded, std::move(stdout_bytes), stderr_text, {}, exit_code};
    }
    return {ProcessOutcomeKind::TechnicalFailure, {}, stderr_text,
            "PROCESS_EXIT_NONZERO: " + std::to_string(exit_code), exit_code};
#endif
}

}  // namespace madre::kernel
