#pragma once

#include <cstdint>
#include <map>
#include <string>
#include <vector>

namespace madre::kernel {

constexpr std::uint16_t kProtocolVersion = 1;

enum class MessageType : std::uint16_t {
    Hello = 1,
    HelloResponse = 2,
    Submit = 10,
    SubmitResponse = 11,
    Status = 20,
    StatusResponse = 21,
    Result = 30,
    ResultResponse = 31,
    Acknowledge = 40,
    AcknowledgeResponse = 41,
    Cancel = 50,
    CancelResponse = 51,
    ListEngines = 60,
    ListEnginesResponse = 61,
    EngineStatus = 70,
    EngineStatusResponse = 71,
    Error = 90,
};

struct Frame {
    MessageType type{};
    std::uint64_t correlation_id{};
    std::map<std::string, std::string> metadata;
    std::vector<std::uint8_t> payload;
};

Frame read_frame(int fd);
void write_frame(int fd, const Frame& frame);
std::string metadata_value(const Frame& frame, const std::string& key);

}  // namespace madre::kernel
