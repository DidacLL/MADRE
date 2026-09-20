#include "store.hpp"

#include <sqlite3.h>

#include <stdexcept>
#include <string>

namespace madre::kernel {
namespace {

void check(int rc, sqlite3* db, const char* action) {
    if (rc != SQLITE_OK && rc != SQLITE_DONE && rc != SQLITE_ROW) {
        throw std::runtime_error(std::string(action) + ": " + sqlite3_errmsg(db));
    }
}

class Statement {
public:
    Statement(sqlite3* db, const char* sql) : db_(db) {
        check(sqlite3_prepare_v2(db_, sql, -1, &statement_, nullptr), db_, "prepare SQLite statement");
    }
    ~Statement() { sqlite3_finalize(statement_); }
    sqlite3_stmt* get() const { return statement_; }
private:
    sqlite3* db_;
    sqlite3_stmt* statement_{};
};

std::string column_text(sqlite3_stmt* statement, int column) {
    const auto* text = sqlite3_column_text(statement, column);
    return text == nullptr ? std::string{} : reinterpret_cast<const char*>(text);
}

WorkRecord read_work(sqlite3_stmt* statement) {
    return WorkRecord{
        column_text(statement, 0),
        column_text(statement, 1),
        column_text(statement, 2),
        column_text(statement, 3),
        column_text(statement, 4),
        sqlite3_column_int(statement, 5) != 0,
        sqlite3_column_int(statement, 6) != 0,
    };
}

void bind_text(sqlite3* db, sqlite3_stmt* statement, int index, const std::string& value) {
    check(sqlite3_bind_text(statement, index, value.c_str(), -1, SQLITE_TRANSIENT), db, "bind SQLite text");
}

}  // namespace

WorkStore::WorkStore(const std::filesystem::path& database_path) {
    std::filesystem::create_directories(database_path.parent_path());
    const auto rc = sqlite3_open_v2(database_path.string().c_str(), &db_,
                                    SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX,
                                    nullptr);
    if (rc != SQLITE_OK) {
        const std::string message = db_ == nullptr ? "open SQLite database" : sqlite3_errmsg(db_);
        if (db_ != nullptr) {
            sqlite3_close(db_);
            db_ = nullptr;
        }
        throw std::runtime_error(message);
    }
    sqlite3_busy_timeout(db_, 5000);
    check(sqlite3_exec(db_, "PRAGMA journal_mode=WAL;", nullptr, nullptr, nullptr), db_, "enable SQLite WAL");
    constexpr const char* schema = R"SQL(
CREATE TABLE IF NOT EXISTS work (
    id TEXT PRIMARY KEY,
    work_type TEXT NOT NULL,
    state TEXT NOT NULL CHECK(state IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    created_at_ms INTEGER NOT NULL,
    input_path TEXT NOT NULL,
    result_path TEXT NOT NULL,
    acknowledged INTEGER NOT NULL DEFAULT 0,
    cancel_requested INTEGER NOT NULL DEFAULT 0,
    technical_failure TEXT
);
CREATE INDEX IF NOT EXISTS work_queue_idx ON work(state, created_at_ms);
)SQL";
    check(sqlite3_exec(db_, schema, nullptr, nullptr, nullptr), db_, "initialize Work schema");
}

WorkStore::~WorkStore() {
    if (db_ != nullptr) {
        sqlite3_close(db_);
    }
}

void WorkStore::submit(const WorkRecord& work, std::int64_t created_at_ms) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, "INSERT INTO work(id,work_type,state,created_at_ms,input_path,result_path,acknowledged,cancel_requested) VALUES(?,?,?,?,?,?,0,0)");
    bind_text(db_, statement.get(), 1, work.id);
    bind_text(db_, statement.get(), 2, work.work_type);
    bind_text(db_, statement.get(), 3, work.state);
    check(sqlite3_bind_int64(statement.get(), 4, created_at_ms), db_, "bind creation time");
    bind_text(db_, statement.get(), 5, work.input_path.string());
    bind_text(db_, statement.get(), 6, work.result_path.string());
    check(sqlite3_step(statement.get()), db_, "insert Work");
}

std::optional<WorkRecord> WorkStore::find(const std::string& id) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, "SELECT id,work_type,state,input_path,result_path,acknowledged,cancel_requested FROM work WHERE id=?");
    bind_text(db_, statement.get(), 1, id);
    const auto rc = sqlite3_step(statement.get());
    if (rc == SQLITE_DONE) {
        return std::nullopt;
    }
    check(rc, db_, "read Work");
    return read_work(statement.get());
}

std::optional<WorkRecord> WorkStore::next_queued() {
    std::lock_guard lock(mutex_);
    Statement statement(db_, "SELECT id,work_type,state,input_path,result_path,acknowledged,cancel_requested FROM work WHERE state='QUEUED' ORDER BY created_at_ms,id LIMIT 1");
    const auto rc = sqlite3_step(statement.get());
    if (rc == SQLITE_DONE) {
        return std::nullopt;
    }
    check(rc, db_, "read queued Work");
    return read_work(statement.get());
}

bool WorkStore::mark_running(const std::string& id) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, "UPDATE work SET state='RUNNING' WHERE id=? AND state='QUEUED' AND cancel_requested=0");
    bind_text(db_, statement.get(), 1, id);
    check(sqlite3_step(statement.get()), db_, "mark Work running");
    return sqlite3_changes(db_) == 1;
}

void WorkStore::mark_succeeded(const std::string& id) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, "UPDATE work SET state='SUCCEEDED' WHERE id=? AND state='RUNNING'");
    bind_text(db_, statement.get(), 1, id);
    check(sqlite3_step(statement.get()), db_, "mark Work succeeded");
}

void WorkStore::mark_failed(const std::string& id, const std::string& technical_failure) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, "UPDATE work SET state='FAILED',technical_failure=? WHERE id=? AND state='RUNNING'");
    bind_text(db_, statement.get(), 1, technical_failure);
    bind_text(db_, statement.get(), 2, id);
    check(sqlite3_step(statement.get()), db_, "mark Work failed");
}

void WorkStore::mark_cancelled(const std::string& id) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, "UPDATE work SET state='CANCELLED' WHERE id=? AND state='RUNNING'");
    bind_text(db_, statement.get(), 1, id);
    check(sqlite3_step(statement.get()), db_, "mark Work cancelled");
}

std::string WorkStore::cancel(const std::string& id) {
    std::lock_guard lock(mutex_);
    Statement find_statement(db_, "SELECT state FROM work WHERE id=?");
    bind_text(db_, find_statement.get(), 1, id);
    const auto rc = sqlite3_step(find_statement.get());
    if (rc == SQLITE_DONE) {
        return {};
    }
    check(rc, db_, "read Work for cancellation");
    const auto state = column_text(find_statement.get(), 0);
    if (state == "QUEUED") {
        Statement cancel_statement(db_, "UPDATE work SET state='CANCELLED',cancel_requested=1 WHERE id=? AND state='QUEUED'");
        bind_text(db_, cancel_statement.get(), 1, id);
        check(sqlite3_step(cancel_statement.get()), db_, "cancel queued Work");
        return "CANCELLED";
    }
    if (state == "RUNNING") {
        Statement request_statement(db_, "UPDATE work SET cancel_requested=1 WHERE id=? AND state='RUNNING'");
        bind_text(db_, request_statement.get(), 1, id);
        check(sqlite3_step(request_statement.get()), db_, "request running Work cancellation");
        return "RUNNING";
    }
    return state;
}

bool WorkStore::cancel_requested(const std::string& id) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, "SELECT cancel_requested FROM work WHERE id=?");
    bind_text(db_, statement.get(), 1, id);
    const auto rc = sqlite3_step(statement.get());
    if (rc == SQLITE_DONE) {
        return false;
    }
    check(rc, db_, "read cancellation request");
    return sqlite3_column_int(statement.get(), 0) != 0;
}

bool WorkStore::acknowledge(const std::string& id) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, "UPDATE work SET acknowledged=1 WHERE id=? AND state='SUCCEEDED' AND acknowledged=0");
    bind_text(db_, statement.get(), 1, id);
    check(sqlite3_step(statement.get()), db_, "acknowledge Work result");
    return sqlite3_changes(db_) == 1;
}

}  // namespace madre::kernel
