#include "protocol.hpp"

#include <array>
#include <cstdint>
#include <stdexcept>
#include <sys/socket.h>
#include <thread>
#include <unistd.h>
#include <vector>

namespace {
void require(bool condition, const char* message) {
    if (!condition) {
        throw std::runtime_error(message);
    }
}

void put_u16(std::uint8_t* p, std::uint16_t value) {
    p[0] = static_cast<std::uint8_t>((value >> 8U) & 0xffU);
    p[1] = static_cast<std::uint8_t>(value & 0xffU);
}

void put_u64(std::uint8_t* p, std::uint64_t value) {
    for (int i = 7; i >= 0; --i) {
        p[i] = static_cast<std::uint8_t>(value & 0xffU);
        value >>= 8U;
    }
}

void write_exact(int fd, const void* data, std::size_t size) {
    const auto* bytes = static_cast<const std::uint8_t*>(data);
    std::size_t written = 0;
    while (written < size) {
        const auto count = ::write(fd, bytes + written, size - written);
        require(count > 0, "raw test frame write failed");
        written += static_cast<std::size_t>(count);
    }
}

std::array<std::uint8_t, 28> header(std::uint16_t framing_version, std::uint64_t payload_length) {
    std::array<std::uint8_t, 28> value{};
    value[0] = 'M';
    value[1] = 'A';
    value[2] = 'D';
    value[3] = 'R';
    put_u16(value.data() + 4, framing_version);
    put_u16(value.data() + 6, static_cast<std::uint16_t>(madre::kernel::MessageType::Hello));
    put_u64(value.data() + 8, 7);
    put_u64(value.data() + 20, payload_length);
    return value;
}

void round_trip() {
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
}

void incompatible_framing_is_rejected_before_body_read() {
    int fds[2]{};
    require(::socketpair(AF_UNIX, SOCK_STREAM, 0, fds) == 0, "socketpair failed");
    const auto raw = header(static_cast<std::uint16_t>(madre::kernel::kFramingVersion + 1U), 0);
    write_exact(fds[0], raw.data(), raw.size());

    bool rejected = false;
    try {
        static_cast<void>(madre::kernel::read_frame(fds[1]));
    } catch (const madre::kernel::FramingError&) {
        rejected = true;
    }
    ::close(fds[0]);
    ::close(fds[1]);
    require(rejected, "unsupported framing version was not rejected");
}

void oversized_c1_payload_is_rejected_before_body_allocation() {
    int fds[2]{};
    require(::socketpair(AF_UNIX, SOCK_STREAM, 0, fds) == 0, "socketpair failed");
    const auto raw = header(
        madre::kernel::kFramingVersion,
        static_cast<std::uint64_t>(madre::kernel::kMaxC1TextGenerationOpaquePayloadBytes) + 1U);
    write_exact(fds[0], raw.data(), raw.size());

    bool read_rejected = false;
    try {
        static_cast<void>(madre::kernel::read_frame(fds[1]));
    } catch (const madre::kernel::FramingError&) {
        read_rejected = true;
    }
    require(read_rejected, ">1 MiB native frame receive was not rejected");

    madre::kernel::Frame oversized{
        madre::kernel::MessageType::ResultResponse,
        9,
        {},
        std::vector<std::uint8_t>(madre::kernel::kMaxC1TextGenerationOpaquePayloadBytes + 1U),
    };
    bool write_rejected = false;
    try {
        madre::kernel::write_frame(fds[0], oversized);
    } catch (const madre::kernel::FramingError&) {
        write_rejected = true;
    }
    ::close(fds[0]);
    ::close(fds[1]);
    require(write_rejected, ">1 MiB native frame send was not rejected");
}
}  // namespace

int main() {
    round_trip();
    incompatible_framing_is_rejected_before_body_read();
    oversized_c1_payload_is_rejected_before_body_allocation();
    return 0;
}
