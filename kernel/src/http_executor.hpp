#pragma once

#include "invocation.hpp"

#include <cstdint>
#include <functional>
#include <optional>
#include <string>
#include <vector>

namespace madre::kernel {

enum class HttpOutcomeKind {
    Succeeded,
    DefiniteFailure,
    CancelledBeforeSubmission,
    UnknownCompletion,
    Stopped,
};

struct HttpOutcome {
    HttpOutcomeKind kind{HttpOutcomeKind::DefiniteFailure};
    std::vector<std::uint8_t> response_body;
    std::string technical_failure;
    std::optional<int> http_status;
};

class HttpExecutor {
public:
    HttpExecutor();
    ~HttpExecutor();
    HttpExecutor(const HttpExecutor&) = delete;
    HttpExecutor& operator=(const HttpExecutor&) = delete;

    bool dispatchable(const HttpInvocationSpec& invocation) const;
    HttpOutcome execute(
        const HttpInvocationSpec& invocation,
        const std::vector<std::uint8_t>& body,
        const std::function<bool()>& cancellation_requested,
        const std::function<bool()>& kernel_stopping,
        std::optional<std::int64_t> stop_at_ms,
        bool timeout_wins) const;
};

}  // namespace madre::kernel
