#include "http_executor.hpp"

#include "protocol.hpp"

#include <curl/curl.h>

#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>

namespace madre::kernel {
namespace {

std::int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::system_clock::now().time_since_epoch()).count();
}

enum class StopReason { None, Cancellation, KernelStopping, Timeout };

struct TransferContext {
    std::vector<std::uint8_t> response;
    bool overflow{};
    const std::function<bool()>* cancellation_requested{};
    const std::function<bool()>* kernel_stopping{};
    std::optional<std::int64_t> stop_at_ms;
    StopReason stop_reason{StopReason::None};
};

std::size_t write_response(char* data, std::size_t size, std::size_t count, void* userdata) {
    auto& context = *static_cast<TransferContext*>(userdata);
    const std::size_t amount = size * count;
    if (amount == 0) return 0;
    if (context.response.size() + amount > kMaxBoundedPayloadBytes) {
        context.overflow = true;
        return 0;
    }
    const auto* bytes = reinterpret_cast<const std::uint8_t*>(data);
    context.response.insert(context.response.end(), bytes, bytes + amount);
    return amount;
}

int progress(void* userdata, curl_off_t, curl_off_t, curl_off_t, curl_off_t) {
    auto& context = *static_cast<TransferContext*>(userdata);
    if ((*context.kernel_stopping)()) {
        context.stop_reason = StopReason::KernelStopping;
        return 1;
    }
    if ((*context.cancellation_requested)()) {
        context.stop_reason = StopReason::Cancellation;
        return 1;
    }
    if (context.stop_at_ms && now_ms() >= *context.stop_at_ms) {
        context.stop_reason = StopReason::Timeout;
        return 1;
    }
    return 0;
}

std::string curl_failure(CURLcode code) {
    return std::string(curl_easy_strerror(code));
}

std::optional<int> response_status(CURL* handle) {
    long status = 0;
    if (curl_easy_getinfo(handle, CURLINFO_RESPONSE_CODE, &status) != CURLE_OK || status <= 0) return std::nullopt;
    return static_cast<int>(status);
}

bool request_may_have_been_submitted(CURL* handle) {
    long request_size = 0;
    curl_off_t uploaded = 0;
    if (curl_easy_getinfo(handle, CURLINFO_REQUEST_SIZE, &request_size) == CURLE_OK && request_size > 0) return true;
    if (curl_easy_getinfo(handle, CURLINFO_SIZE_UPLOAD_T, &uploaded) == CURLE_OK && uploaded > 0) return true;
    return false;
}

bool header_value_has_line_break(std::string_view value) {
    return value.find_first_of("\r\n") != std::string_view::npos;
}

struct HeaderList {
    curl_slist* value{};
    ~HeaderList() { if (value != nullptr) curl_slist_free_all(value); }
    void append(const std::string& line) {
        curl_slist* next = curl_slist_append(value, line.c_str());
        if (next == nullptr) throw std::runtime_error("allocate libcurl header list");
        value = next;
    }
};

}  // namespace

HttpExecutor::HttpExecutor() {
    const auto rc = curl_global_init(CURL_GLOBAL_DEFAULT);
    if (rc != CURLE_OK) throw std::runtime_error("initialize libcurl transport");
}

HttpExecutor::~HttpExecutor() { curl_global_cleanup(); }

bool HttpExecutor::dispatchable(const HttpInvocationSpec& invocation) const {
    for (const auto& header : invocation.headers) {
        if (header.source == HttpHeaderSource::Environment && std::getenv(header.environment_variable.c_str()) == nullptr) return false;
    }
    return true;
}

HttpOutcome HttpExecutor::execute(
    const HttpInvocationSpec& invocation,
    const std::vector<std::uint8_t>& body,
    const std::function<bool()>& cancellation_requested,
    const std::function<bool()>& kernel_stopping,
    std::optional<std::int64_t> stop_at_ms,
    bool timeout_wins) const {
    if (body.size() > kMaxBoundedPayloadBytes) {
        return {HttpOutcomeKind::DefiniteFailure, {}, "HTTP_REQUEST_BODY_TOO_LARGE", std::nullopt};
    }

    CURL* raw = curl_easy_init();
    if (raw == nullptr) return {HttpOutcomeKind::DefiniteFailure, {}, "HTTP_TRANSPORT_INITIALIZATION_FAILED", std::nullopt};
    struct EasyCleanup { CURL* handle; ~EasyCleanup() { curl_easy_cleanup(handle); } } cleanup{raw};

    HeaderList headers;
    try {
        for (const auto& header : invocation.headers) {
            std::string value;
            if (header.source == HttpHeaderSource::Literal) {
                value = header.literal_value;
            } else {
                const char* secret = std::getenv(header.environment_variable.c_str());
                if (secret == nullptr) {
                    return {HttpOutcomeKind::DefiniteFailure, {}, "HTTP_LATE_BOUND_CREDENTIAL_UNAVAILABLE", std::nullopt};
                }
                value = header.prefix + secret + header.suffix;
            }
            if (header_value_has_line_break(value)) {
                return {HttpOutcomeKind::DefiniteFailure, {}, "HTTP_HEADER_VALUE_CONTAINS_CR_OR_LF", std::nullopt};
            }
            headers.append(header.name + ": " + value);
        }
    } catch (const std::exception&) {
        return {HttpOutcomeKind::DefiniteFailure, {}, "HTTP_HEADER_PREPARATION_FAILED", std::nullopt};
    }

    TransferContext context;
    context.cancellation_requested = &cancellation_requested;
    context.kernel_stopping = &kernel_stopping;
    context.stop_at_ms = stop_at_ms;

    const char* payload = body.empty() ? "" : reinterpret_cast<const char*>(body.data());
    curl_easy_setopt(raw, CURLOPT_URL, invocation.uri.c_str());
    curl_easy_setopt(raw, CURLOPT_POST, 1L);
    curl_easy_setopt(raw, CURLOPT_POSTFIELDS, payload);
    curl_easy_setopt(raw, CURLOPT_POSTFIELDSIZE_LARGE, static_cast<curl_off_t>(body.size()));
    curl_easy_setopt(raw, CURLOPT_HTTPHEADER, headers.value);
    curl_easy_setopt(raw, CURLOPT_WRITEFUNCTION, write_response);
    curl_easy_setopt(raw, CURLOPT_WRITEDATA, &context);
    curl_easy_setopt(raw, CURLOPT_NOPROGRESS, 0L);
    curl_easy_setopt(raw, CURLOPT_XFERINFOFUNCTION, progress);
    curl_easy_setopt(raw, CURLOPT_XFERINFODATA, &context);
    curl_easy_setopt(raw, CURLOPT_NOSIGNAL, 1L);
    curl_easy_setopt(raw, CURLOPT_FOLLOWLOCATION, 0L);
    curl_easy_setopt(raw, CURLOPT_SSL_VERIFYPEER, 1L);
    curl_easy_setopt(raw, CURLOPT_SSL_VERIFYHOST, 2L);
    curl_easy_setopt(raw, CURLOPT_PROTOCOLS_STR, "http,https");
    if (stop_at_ms) {
        const auto remaining = std::max<std::int64_t>(1, *stop_at_ms - now_ms());
        curl_easy_setopt(raw, CURLOPT_TIMEOUT_MS, static_cast<long>(std::min<std::int64_t>(remaining, 2'147'483'647L)));
    }

    const CURLcode rc = curl_easy_perform(raw);
    const auto status = response_status(raw);
    const bool may_have_submitted = request_may_have_been_submitted(raw);

    if (context.stop_reason == StopReason::KernelStopping) {
        return {HttpOutcomeKind::Stopped, {}, "KERNEL_STOPPED_DURING_HTTP_ATTEMPT", status};
    }
    if (context.stop_reason == StopReason::Cancellation) {
        if (may_have_submitted) {
            return {HttpOutcomeKind::UnknownCompletion, {}, "HTTP_CANCELLED_AFTER_SUBMISSION: remote completion unknown", status};
        }
        return {HttpOutcomeKind::CancelledBeforeSubmission, {}, "HTTP_CANCELLED_BEFORE_SUBMISSION", status};
    }
    if (context.stop_reason == StopReason::Timeout || rc == CURLE_OPERATION_TIMEDOUT) {
        const std::string reason = timeout_wins ? "ATTEMPT_TIMEOUT" : "DEADLINE_EXPIRED_DURING_ATTEMPT";
        if (may_have_submitted) {
            return {HttpOutcomeKind::UnknownCompletion, {}, reason + ": HTTP request may have reached remote target", status};
        }
        return {HttpOutcomeKind::DefiniteFailure, {}, reason + ": before HTTP submission", status};
    }

    if (status) {
        if (context.overflow) {
            return {HttpOutcomeKind::DefiniteFailure, {}, "HTTP_RESPONSE_BODY_TOO_LARGE", status};
        }
        if (rc != CURLE_OK) {
            return {HttpOutcomeKind::DefiniteFailure, {}, "HTTP_RESPONSE_TRANSFER_FAILURE: " + curl_failure(rc), status};
        }
        if (*status >= 200 && *status < 300) {
            return {HttpOutcomeKind::Succeeded, std::move(context.response), {}, status};
        }
        return {HttpOutcomeKind::DefiniteFailure, {}, "HTTP_STATUS_" + std::to_string(*status), status};
    }

    if (rc == CURLE_OK) {
        return {HttpOutcomeKind::DefiniteFailure, {}, "HTTP_RESPONSE_STATUS_UNAVAILABLE", std::nullopt};
    }
    if (may_have_submitted) {
        return {HttpOutcomeKind::UnknownCompletion, {}, "HTTP_TRANSPORT_LOST_AFTER_SUBMISSION: " + curl_failure(rc), std::nullopt};
    }
    return {HttpOutcomeKind::DefiniteFailure, {}, "HTTP_TRANSPORT_FAILURE_BEFORE_SUBMISSION: " + curl_failure(rc), std::nullopt};
}

}  // namespace madre::kernel
