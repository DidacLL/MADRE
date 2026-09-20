#include "protocol.hpp"

#include <algorithm>
#include <cerrno>
#include <chrono>
#include <cstdlib>
#include <fcntl.h>
#include <iostream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <thread>
#include <unistd.h>
#include <vector>

namespace madre::kernel {
namespace {
using namespace std::chrono_literals;

struct Options {
    std::string engine_id;
    int delay_ms{};
};

Options parse_options(int argc, char** argv) {
    Options options;
    for (int i = 1; i < argc; ++i) {
        const std::string argument = argv[i];
        if (argument == "--engine-id" && i + 1 < argc) {
            options.engine_id = argv[++i];
        } else if (argument == "--delay-ms" && i + 1 < argc) {
            options.delay_ms = std::stoi(argv[++i]);
        } else {
            throw std::runtime_error("usage: madre-fake-worker --engine-id <id> --delay-ms <ms>");
        }
    }
    if (options.engine_id.empty() || options.delay_ms < 0) {
        throw std::runtime_error("usage: madre-fake-worker --engine-id <id> --delay-ms <ms>");
    }
    return options;
}

bool equals_payload(const std::vector<std::uint8_t>& payload, std::string_view text) {
    return payload.size() == text.size() && std::equal(payload.begin(), payload.end(), text.begin());
}

void execute(const Options& options, const Frame& request) {
    if (request.type != MessageType::WorkerExecute) {
        Frame failure{MessageType::WorkerFailure, request.correlation_id,
                      {{"technical_failure", "WORKER_PROTOCOL_FAILURE: unsupported request"}}, {}};
        write_frame(STDOUT_FILENO, failure);
        return;
    }
    const auto engine = metadata_value(request, "engine_id");
    if (engine != options.engine_id) {
        Frame failure{MessageType::WorkerFailure, request.correlation_id,
                      {{"technical_failure", "WORKER_PROTOCOL_FAILURE: engine identity mismatch"}}, {}};
        write_frame(STDOUT_FILENO, failure);
        return;
    }

    const auto started_at_ms = std::stoll(metadata_value(request, "attempt_started_at_ms"));
    const auto complete_at_ms = started_at_ms + options.delay_ms;
    const auto current_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                                std::chrono::system_clock::now().time_since_epoch())
                                .count();
    if (complete_at_ms > current_ms) {
        std::this_thread::sleep_for(std::chrono::milliseconds(complete_at_ms - current_ms));
    }

    constexpr std::string_view crash = "__C3_WORKER_CRASH__";
    if (equals_payload(request.payload, crash)) {
        std::_Exit(86);
    }

    constexpr std::string_view hang = "__C3_HANG_AFTER_DELAY__";
    if (equals_payload(request.payload, hang)) {
        while (true) {
            std::this_thread::sleep_for(std::chrono::hours(1));
        }
    }

    constexpr std::string_view partial_stall = "__C3_PARTIAL_FRAME_STALL__";
    if (equals_payload(request.payload, partial_stall)) {
        constexpr char partial_frame[] = {'M', 'A', 'D', 'R'};
        if (::write(STDOUT_FILENO, partial_frame, sizeof(partial_frame)) < 0) {
            std::_Exit(87);
        }
        while (true) {
            std::this_thread::sleep_for(std::chrono::hours(1));
        }
    }

    constexpr std::string_view descriptor_check = "__C3_CHECK_NO_EXTRA_FDS__";
    if (equals_payload(request.payload, descriptor_check)) {
        for (int fd = 3; fd < 256; ++fd) {
            errno = 0;
            if (::fcntl(fd, F_GETFD) != -1 || errno != EBADF) {
                Frame failure{
                    MessageType::WorkerFailure,
                    request.correlation_id,
                    {{"technical_failure",
                      "WORKER_FD_LEAK: inherited descriptor " + std::to_string(fd)}},
                    {}};
                write_frame(STDOUT_FILENO, failure);
                return;
            }
        }
    }

    constexpr std::string_view forced_failure = "__C2_TECHNICAL_FAILURE__";
    if (equals_payload(request.payload, forced_failure)) {
        Frame failure{MessageType::WorkerFailure, request.correlation_id,
                      {{"technical_failure", "FAKE_TECHNICAL_FAILURE: deterministic C2 test engine failure"}}, {}};
        write_frame(STDOUT_FILENO, failure);
        return;
    }

    constexpr std::string_view prefix = "fake:";
    if (request.payload.size() > kMaxC1TextGenerationOpaquePayloadBytes - prefix.size()) {
        Frame failure{MessageType::WorkerFailure, request.correlation_id,
                      {{"technical_failure", "FAKE_RESULT_TOO_LARGE: bounded result limit exceeded"}}, {}};
        write_frame(STDOUT_FILENO, failure);
        return;
    }
    std::vector<std::uint8_t> result(prefix.begin(), prefix.end());
    result.insert(result.end(), request.payload.begin(), request.payload.end());
    write_frame(STDOUT_FILENO, Frame{MessageType::WorkerResult, request.correlation_id, {}, std::move(result)});
}

}  // namespace
}  // namespace madre::kernel

int main(int argc, char** argv) {
    try {
        const auto options = madre::kernel::parse_options(argc, argv);
        while (true) {
            madre::kernel::Frame request;
            try {
                request = madre::kernel::read_frame(STDIN_FILENO);
            } catch (const std::runtime_error& ex) {
                if (std::string_view(ex.what()) == "peer closed framed IPC") {
                    return 0;
                }
                throw;
            }
            madre::kernel::execute(options, request);
        }
    } catch (const std::exception& ex) {
        std::cerr << "madre-fake-worker: " << ex.what() << '\n';
        return 1;
    }
}
