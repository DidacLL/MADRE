#pragma once

#include <cstddef>
#include <cstdint>
#include <map>
#include <stdexcept>
#include <string>
#include <vector>

namespace madre::kernel {

constexpr std::uint16_t kFramingVersion = 1;
constexpr int kMinKernelProtocolVersion = 4;
constexpr int kMaxKernelProtocolVersion = 4;
constexpr std::size_t kMaxBoundedPayloadBytes = 1024U * 1024U;
constexpr std::size_t kMaxBoundedStderrBytes = 64U * 1024U;

class FramingError final : public std::runtime_error { public: using std::runtime_error::runtime_error; };

enum class MessageType : std::uint16_t {
    Hello = 1, HelloResponse = 2,
    Submit = 10, SubmitResponse = 11,
    Status = 20, StatusResponse = 21,
    Result = 30, ResultResponse = 31,
    Release = 40, ReleaseResponse = 41,
    Cancel = 50, CancelResponse = 51,
    Error = 90,
};

struct Frame {
    MessageType type{};
    std::uint64_t correlation_id{};
    std::map<std::string, std::string> metadata;
    std::vector<std::uint8_t> payload;
};

std::vector<std::uint8_t> encode_frame(const Frame& frame);
Frame read_frame(int fd);
void write_frame(int fd, const Frame& frame);
std::string metadata_value(const Frame& frame, const std::string& key);

}  // namespace madre::kernel
