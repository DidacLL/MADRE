#pragma once

#include <cstdint>
#include <filesystem>
#include <functional>
#include <optional>
#include <string>
#include <vector>

namespace madre::kernel {

struct ProcessInvocationSpec {
    std::string candidate_id;
    std::filesystem::path executable;
    std::vector<std::string> arguments;
    std::string target_identity;
};

enum class ProcessOutcomeKind {
    Succeeded,
    TechnicalFailure,
    Cancelled,
    TimedOut,
    Stopped,
};

struct ProcessOutcome {
    ProcessOutcomeKind kind{ProcessOutcomeKind::TechnicalFailure};
    std::vector<std::uint8_t> stdout_payload;
    std::string stderr_text;
    std::string technical_failure;
    std::optional<int> exit_code;
};

class ProcessExecutor {
public:
    bool executable_available(const std::filesystem::path& executable) const;

    ProcessOutcome execute(
        const ProcessInvocationSpec& invocation,
        const std::vector<std::uint8_t>& stdin_payload,
        const std::function<bool()>& cancellation_requested,
        const std::function<bool()>& kernel_stopping,
        std::optional<std::int64_t> stop_at_ms,
        bool timeout_wins) const;
};

}  // namespace madre::kernel
