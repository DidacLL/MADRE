#pragma once

#include "invocation.hpp"

#include <cstdint>
#include <filesystem>
#include <mutex>
#include <optional>
#include <string>
#include <vector>

struct sqlite3;

namespace madre::kernel {

struct AttemptRecord {
    int attempt_number{};
    std::string candidate_id;
    std::string kind;
    std::string target_identity;
    std::int64_t started_at_ms{};
    std::optional<std::int64_t> ended_at_ms;
    std::string state;
    std::string technical_failure;
    std::optional<int> exit_code;
    std::optional<int> http_status;
};

struct WorkRecord {
    std::string id;
    std::string state;
    std::string urgency;
    std::int64_t created_at_ms{};
    std::int64_t eligible_at_ms{};
    std::int64_t next_attempt_at_ms{};
    std::optional<std::int64_t> deadline_ms;
    std::optional<std::int64_t> attempt_timeout_ms;
    int max_attempts{1};
    std::int64_t retry_delay_ms{};
    std::string retry_safety;
    int attempt_count{};
    std::filesystem::path result_path;
    bool released{};
    bool cancel_requested{};
    std::string technical_failure;
    std::vector<ConcretePhysicalInvocationSpec> candidates;
    std::optional<AttemptRecord> latest_attempt;
};

class WorkStore {
public:
    explicit WorkStore(const std::filesystem::path& database_path);
    ~WorkStore();
    WorkStore(const WorkStore&) = delete;
    WorkStore& operator=(const WorkStore&) = delete;

    void submit(const WorkRecord& work);
    std::optional<WorkRecord> find(const std::string& id);
    std::optional<WorkRecord> next_eligible(std::int64_t now_ms);
    void expire_queued_deadlines(std::int64_t now_ms);
    void fail_queued(const std::string& id, const std::string& technical_failure);
    int begin_attempt(const std::string& id, const ConcretePhysicalInvocationSpec& candidate, std::int64_t started_at_ms);
    void finish_success(const std::string& id, int attempt_number, std::optional<int> exit_code,
                        std::optional<int> http_status, std::int64_t ended_at_ms);
    void finish_cancelled(const std::string& id, int attempt_number, const std::string& technical_failure,
                          std::optional<int> exit_code, std::optional<int> http_status, std::int64_t ended_at_ms);
    void finish_definite_failure(const std::string& id, int attempt_number, const std::string& attempt_state,
                                 const std::string& technical_failure, std::optional<int> exit_code,
                                 std::optional<int> http_status, std::int64_t ended_at_ms);
    void finish_unknown_completion(const std::string& id, int attempt_number, const std::string& technical_failure,
                                   std::optional<int> http_status, std::int64_t ended_at_ms);
    void recover_interrupted(std::int64_t now_ms);
    std::string cancel(const std::string& id);
    bool cancel_requested(const std::string& id);
    bool release(const std::string& id);

private:
    sqlite3* db_{};
    std::mutex mutex_;
};

}  // namespace madre::kernel
