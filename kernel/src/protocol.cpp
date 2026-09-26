#include "protocol.hpp"

#include <algorithm>
#include <array>
#include <cerrno>
#include <climits>
#include <cstring>
#include <stdexcept>
#include <string_view>

#ifdef _WIN32
#include <io.h>
#else
#include <unistd.h>
#endif

namespace madre::kernel {
namespace {
constexpr std::array<std::uint8_t, 4> kMagic{'M', 'A', 'D', 'R'};
constexpr std::size_t kHeaderSize = 28;
constexpr std::uint32_t kMaxMetadata = 1024U * 1024U;

void read_exact(int fd, void* data, std::size_t size) {
    auto* out = static_cast<std::uint8_t*>(data);
    std::size_t done = 0;
    while (done < size) {
#ifdef _WIN32
        const auto amount = static_cast<unsigned int>(std::min<std::size_t>(size - done, static_cast<std::size_t>(INT_MAX)));
        const int n = ::_read(fd, out + done, amount);
#else
        const auto n = ::read(fd, out + done, size - done);
#endif
        if (n == 0) throw std::runtime_error("peer closed framed IPC");
        if (n < 0) {
#ifndef _WIN32
            if (errno == EINTR) continue;
#endif
            throw std::runtime_error(std::string("IPC read failed: ") + std::strerror(errno));
        }
        done += static_cast<std::size_t>(n);
    }
}

void write_exact(int fd, const void* data, std::size_t size) {
    const auto* in = static_cast<const std::uint8_t*>(data);
    std::size_t done = 0;
    while (done < size) {
#ifdef _WIN32
        const auto amount = static_cast<unsigned int>(std::min<std::size_t>(size - done, static_cast<std::size_t>(INT_MAX)));
        const int n = ::_write(fd, in + done, amount);
#else
        const auto n = ::write(fd, in + done, size - done);
#endif
        if (n < 0) {
#ifndef _WIN32
            if (errno == EINTR) continue;
#endif
            throw std::runtime_error(std::string("IPC write failed: ") + std::strerror(errno));
        }
        if (n == 0) throw std::runtime_error("IPC write made no progress");
        done += static_cast<std::size_t>(n);
    }
}

std::uint16_t get_u16(const std::uint8_t* p) {
    return static_cast<std::uint16_t>((static_cast<std::uint16_t>(p[0]) << 8U) | p[1]);
}
std::uint32_t get_u32(const std::uint8_t* p) {
    return (static_cast<std::uint32_t>(p[0]) << 24U) | (static_cast<std::uint32_t>(p[1]) << 16U) |
           (static_cast<std::uint32_t>(p[2]) << 8U) | p[3];
}
std::uint64_t get_u64(const std::uint8_t* p) {
    std::uint64_t value = 0;
    for (int i = 0; i < 8; ++i) value = (value << 8U) | p[i];
    return value;
}
void put_u16(std::uint8_t* p, std::uint16_t value) {
    p[0] = static_cast<std::uint8_t>((value >> 8U) & 0xffU);
    p[1] = static_cast<std::uint8_t>(value & 0xffU);
}
void put_u32(std::uint8_t* p, std::uint32_t value) {
    p[0] = static_cast<std::uint8_t>((value >> 24U) & 0xffU);
    p[1] = static_cast<std::uint8_t>((value >> 16U) & 0xffU);
    p[2] = static_cast<std::uint8_t>((value >> 8U) & 0xffU);
    p[3] = static_cast<std::uint8_t>(value & 0xffU);
}
void put_u64(std::uint8_t* p, std::uint64_t value) {
    for (int i = 7; i >= 0; --i) {
        p[i] = static_cast<std::uint8_t>(value & 0xffU);
        value >>= 8U;
    }
}

std::string encode_metadata(const std::map<std::string, std::string>& metadata) {
    std::string out;
    for (const auto& [key, value] : metadata) {
        if (key.empty() || key.find_first_of("=\r\n") != std::string::npos || value.find_first_of("\r\n") != std::string::npos) {
            throw FramingError("invalid framed metadata");
        }
        out += key;
        out += '=';
        out += value;
        out += '\n';
    }
    return out;
}

std::map<std::string, std::string> decode_metadata(std::string_view encoded) {
    std::map<std::string, std::string> out;
    std::size_t pos = 0;
    while (pos < encoded.size()) {
        const auto end = encoded.find('\n', pos);
        const auto line_end = end == std::string_view::npos ? encoded.size() : end;
        const auto line = encoded.substr(pos, line_end - pos);
        if (!line.empty()) {
            const auto eq = line.find('=');
            if (eq == std::string_view::npos || eq == 0) throw FramingError("malformed framed metadata");
            out.emplace(std::string(line.substr(0, eq)), std::string(line.substr(eq + 1)));
        }
        pos = line_end + 1;
    }
    return out;
}
}  // namespace

Frame read_frame(int fd) {
    std::array<std::uint8_t, kHeaderSize> header{};
    read_exact(fd, header.data(), header.size());
    if (!std::equal(kMagic.begin(), kMagic.end(), header.begin())) throw FramingError("invalid framing magic");
    if (get_u16(header.data() + 4) != kFramingVersion) throw FramingError("unsupported framing version");
    const auto type = get_u16(header.data() + 6);
    const auto correlation = get_u64(header.data() + 8);
    const auto metadata_length = get_u32(header.data() + 16);
    const auto payload_length = get_u64(header.data() + 20);
    if (metadata_length > kMaxMetadata || payload_length > kMaxBoundedPayloadBytes) {
        throw FramingError("frame exceeds bounded physical payload limits");
    }
    std::string metadata(metadata_length, '\0');
    if (!metadata.empty()) read_exact(fd, metadata.data(), metadata.size());
    std::vector<std::uint8_t> payload(static_cast<std::size_t>(payload_length));
    if (!payload.empty()) read_exact(fd, payload.data(), payload.size());
    return Frame{static_cast<MessageType>(type), correlation, decode_metadata(metadata), std::move(payload)};
}

std::vector<std::uint8_t> encode_frame(const Frame& frame) {
    const auto metadata = encode_metadata(frame.metadata);
    if (metadata.size() > kMaxMetadata || frame.payload.size() > kMaxBoundedPayloadBytes) {
        throw FramingError("frame exceeds bounded physical payload limits");
    }
    std::vector<std::uint8_t> encoded(kHeaderSize + metadata.size() + frame.payload.size());
    auto* header = encoded.data();
    std::copy(kMagic.begin(), kMagic.end(), encoded.begin());
    put_u16(header + 4, kFramingVersion);
    put_u16(header + 6, static_cast<std::uint16_t>(frame.type));
    put_u64(header + 8, frame.correlation_id);
    put_u32(header + 16, static_cast<std::uint32_t>(metadata.size()));
    put_u64(header + 20, static_cast<std::uint64_t>(frame.payload.size()));
    std::copy(metadata.begin(), metadata.end(), encoded.begin() + static_cast<std::ptrdiff_t>(kHeaderSize));
    std::copy(frame.payload.begin(), frame.payload.end(), encoded.begin() + static_cast<std::ptrdiff_t>(kHeaderSize + metadata.size()));
    return encoded;
}

void write_frame(int fd, const Frame& frame) {
    const auto encoded = encode_frame(frame);
    write_exact(fd, encoded.data(), encoded.size());
}

std::string metadata_value(const Frame& frame, const std::string& key) {
    const auto it = frame.metadata.find(key);
    if (it == frame.metadata.end()) throw std::runtime_error("missing metadata field: " + key);
    return it->second;
}

}  // namespace madre::kernel
