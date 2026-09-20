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
    std::filesystem::path input_path;
    std::filesystem::path result_path;
    bool acknowledged{};
    bool cancel_requested{};
};

class WorkStore {
public:
    explicit WorkStore(const std::filesystem::path& database_path);
    ~WorkStore();

    WorkStore(const WorkStore&) = delete;
    WorkStore& operator=(const WorkStore&) = delete;

    void submit(const WorkRecord& work, std::int64_t created_at_ms);
    std::optional<WorkRecord> find(const std::string& id);
    std::optional<WorkRecord> next_queued();
    bool mark_running(const std::string& id);
    void mark_succeeded(const std::string& id);
    void mark_failed(const std::string& id, const std::string& technical_failure);
    void mark_cancelled(const std::string& id);
    std::string cancel(const std::string& id);
    bool cancel_requested(const std::string& id);
    bool acknowledge(const std::string& id);

private:
    sqlite3* db_{};
    std::mutex mutex_;
};

}  // namespace madre::kernel
