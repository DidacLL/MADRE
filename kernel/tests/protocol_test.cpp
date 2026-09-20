#include "protocol.hpp"

#include <cstdint>
#include <stdexcept>
#include <sys/socket.h>
#include <thread>
#include <unistd.h>

namespace {
void require(bool condition, const char* message) {
    if (!condition) {
        throw std::runtime_error(message);
    }
}
}

int main() {
    int fds[2]{};
    require(::socketpair(AF_UNIX, SOCK_STREAM, 0, fds) == 0, "socketpair failed");

    madre::kernel::Frame original{
        madre::kernel::MessageType::Submit,
        42,
        {{"work_type", "text-generation/v1"}, {"future_field", "ignored-by-compatible-peer"}},
        {0, 1, 2, 3, 255},
    };

    std::thread writer([&] {
        madre::kernel::write_frame(fds[0], original);
        ::close(fds[0]);
    });

    const auto decoded = madre::kernel::read_frame(fds[1]);
    ::close(fds[1]);
    writer.join();

    require(decoded.type == original.type, "message type changed");
    require(decoded.correlation_id == 42, "correlation id changed");
    require(decoded.metadata.at("work_type") == "text-generation/v1", "work type metadata changed");
    require(decoded.metadata.at("future_field") == "ignored-by-compatible-peer", "unknown metadata was not preserved");
    require(decoded.payload == original.payload, "binary payload changed");
    return 0;
}
