#include "protocol.hpp"
#include "store.hpp"

#include <algorithm>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <mutex>
#include <poll.h>
#include <random>
#include <sstream>
#include <stdexcept>
#include <string>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <thread>
#include <unistd.h>
#include <vector>
#include <csignal>

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

void write_binary_atomic(const fs::path& path, const std::vector<std::uint8_t>& data) {
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

std::vector<std::uint8_t> read_binary(const fs::path& path) {
    std::ifstream stream(path, std::ios::binary);
    if (!stream) {
        throw std::runtime_error("open payload file for read: " + path.string());
    }
    return std::vector<std::uint8_t>(std::istreambuf_iterator<char>(stream), {});
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

class Kernel {
public:
    Kernel(fs::path data_dir, fs::path endpoint, int fake_delay_ms)
        : data_dir_(std::move(data_dir)),
          endpoint_(std::move(endpoint)),
          store_(data_dir_ / "kernel.db"),
          fake_delay_ms_(fake_delay_ms) {
        fs::create_directories(data_dir_ / "work");
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
        start_worker();
        open_server();
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
            auto work = store_.next_queued();
            if (!work) {
                std::unique_lock lock(wake_mutex_);
                wake_.wait_for(lock, 100ms, [this] { return stop_.load(); });
                continue;
            }
            if (!store_.mark_running(work->id)) {
                continue;
            }
            try {
                const int steps = std::max(1, fake_delay_ms_ / 10);
                for (int i = 0; i < steps; ++i) {
                    if (stop_.load()) {
                        return;
                    }
                    if (store_.cancel_requested(work->id)) {
                        store_.mark_cancelled(work->id);
                        goto next_work;
                    }
                    std::this_thread::sleep_for(10ms);
                }
                if (store_.cancel_requested(work->id)) {
                    store_.mark_cancelled(work->id);
                    goto next_work;
                }
                {
                    const auto input = read_binary(work->input_path);
                    constexpr std::string_view prefix = "fake:";
                    std::vector<std::uint8_t> result(prefix.begin(), prefix.end());
                    result.insert(result.end(), input.begin(), input.end());
                    write_binary_atomic(work->result_path, result);
                    store_.mark_succeeded(work->id);
                }
            } catch (const std::exception& ex) {
                store_.mark_failed(work->id, ex.what());
            }
        next_work:
            continue;
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
            if (hello_response.type == MessageType::Error) {
                return;
            }
            const auto request = read_frame(fd);
            write_frame(fd, handle(request));
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
        const auto min_version = std::stoi(metadata_value(request, "min_version"));
        const auto max_version = std::stoi(metadata_value(request, "max_version"));
        if (min_version > kProtocolVersion || max_version < kProtocolVersion) {
            return error_response(request, "VERSION_MISMATCH", "no compatible Kernel protocol version");
        }
        auto out = response(MessageType::HelloResponse, request);
        out.metadata["version"] = std::to_string(kProtocolVersion);
        return out;
    }

    Frame submit(const Frame& request) {
        const auto work_type = metadata_value(request, "work_type");
        if (work_type != "text-generation/v1") {
            return error_response(request, "UNSUPPORTED_WORK_TYPE", "fake engine supports only text-generation/v1");
        }
        const auto id = make_work_id();
        const auto work_dir = data_dir_ / "work" / id;
        const auto input = work_dir / "input.bin";
        const auto result_path = work_dir / "result.bin";
        write_binary_atomic(input, request.payload);
        store_.submit(WorkRecord{id, work_type, "QUEUED", input, result_path, false, false}, now_ms());
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
            out.payload = read_binary(work->result_path);
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
        out.metadata["count"] = "1";
        out.metadata["engine.0.id"] = "fake-local";
        out.metadata["engine.0.work_types"] = "text-generation/v1";
        out.metadata["engine.0.capabilities"] = "";
        out.metadata["engine.0.placement"] = "LOCAL_MACHINE";
        out.metadata["engine.0.availability"] = "AVAILABLE";
        out.metadata["engine.0.warm"] = "true";
        return out;
    }

    Frame engine_status(const Frame& request) {
        const auto id = metadata_value(request, "engine_id");
        if (id != "fake-local") {
            return error_response(request, "ENGINE_NOT_FOUND", "unknown physical engine");
        }
        auto out = response(MessageType::EngineStatusResponse, request);
        out.metadata["engine_id"] = id;
        out.metadata["availability"] = "AVAILABLE";
        out.metadata["warm"] = "true";
        return out;
    }

    fs::path data_dir_;
    fs::path endpoint_;
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
