#pragma once

#include <filesystem>
#include <string>
#include <variant>
#include <vector>

namespace madre::kernel {

struct ProcessInvocationSpec {
    std::string candidate_id;
    std::filesystem::path executable;
    std::vector<std::string> arguments;
    std::filesystem::path payload_path;
    std::string target_identity;
};

enum class HttpHeaderSource { Literal, Environment };

struct HttpHeaderSpec {
    std::string name;
    HttpHeaderSource source{HttpHeaderSource::Literal};
    std::string literal_value;
    std::string environment_variable;
    std::string prefix;
    std::string suffix;
};

struct HttpInvocationSpec {
    std::string candidate_id;
    std::string uri;
    std::vector<HttpHeaderSpec> headers;
    std::filesystem::path payload_path;
    std::string target_identity;
};

// Typed physical extension seam. A future real mechanism extends this algebra
// with its own typed spec plus executor, bounded persistence/wire support and
// dispatch integration. Work lifecycle and scheduling remain above the
// concrete variant; do not replace this with stringly metadata or a plugin
// registry merely to anticipate unknown mechanisms.
using ConcretePhysicalInvocationSpec = std::variant<ProcessInvocationSpec, HttpInvocationSpec>;

inline const std::string& invocation_id(const ConcretePhysicalInvocationSpec& value) {
    return std::visit([](const auto& invocation) -> const std::string& { return invocation.candidate_id; }, value);
}
inline const std::string& target_identity(const ConcretePhysicalInvocationSpec& value) {
    return std::visit([](const auto& invocation) -> const std::string& { return invocation.target_identity; }, value);
}
inline const std::filesystem::path& payload_path(const ConcretePhysicalInvocationSpec& value) {
    return std::visit([](const auto& invocation) -> const std::filesystem::path& { return invocation.payload_path; }, value);
}
inline const char* invocation_kind(const ConcretePhysicalInvocationSpec& value) {
    return std::holds_alternative<ProcessInvocationSpec>(value) ? "PROCESS" : "HTTP";
}

}  // namespace madre::kernel
