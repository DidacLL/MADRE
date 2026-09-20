#pragma once

#include <cstdint>
#include <filesystem>
#include <mutex>
#include <optional>
#include <string>

struct sqlite3;

namespace madre::kernel {

struct WorkRecord {
    std::string id;
    std::string work_type;
    std::string state;
    std::string effort;
    std::string urgency;
    std::string required_capabilities;
    std::string eligible_engine_ids;
    std::string exact_engine_id;
    std::string exact_model_id;
    std::int64_t created_at_ms{};
    std::int64_t eligible_at_ms{};
    std::int64_t next_attempt_at_ms{};
    std::optional<std::int64_t> deadline_ms;
    std::optional<std::int64_t> timeout_ms;
    int max_attempts{1};
    std::int64_t retry_delay_ms{};
    int attempt_count{};
    std::filesystem::path input_path;
    std::filesystem::path result_path;
    bool acknowledged{};
    bool cancel_requested{};
    std::string selected_engine_id;
    std::string selected_model_id;
    std::string technical_failure;
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
    int begin_attempt(const std::string& id, const std::string& engine_id,
                      const std::string& model_id, std::int64_t started_at_ms);
    void finish_success(const std::string& id, int attempt_number, std::int64_t ended_at_ms);
    void finish_cancelled(const std::string& id, int attempt_number, std::int64_t ended_at_ms);
    void finish_retryable_failure(const std::string& id, int attempt_number,
                                  const std::string& attempt_state,
                                  const std::string& technical_failure,
                                  std::int64_t ended_at_ms);
    void recover_interrupted(std::int64_t now_ms);
    std::string cancel(const std::string& id);
    bool cancel_requested(const std::string& id);
    bool acknowledge(const std::string& id);

private:
    sqlite3* db_{};
    std::mutex mutex_;
};

}  // namespace madre::kernel
