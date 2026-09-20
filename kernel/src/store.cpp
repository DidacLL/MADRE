#include "store.hpp"

#include <sqlite3.h>

#include <filesystem>
#include <stdexcept>
#include <string>
#include <vector>

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

std::optional<std::int64_t> column_optional_i64(sqlite3_stmt* statement, int column) {
    if (sqlite3_column_type(statement, column) == SQLITE_NULL) {
        return std::nullopt;
    }
    return sqlite3_column_int64(statement, column);
}

void bind_text(sqlite3* db, sqlite3_stmt* statement, int index, const std::string& value) {
    check(sqlite3_bind_text(statement, index, value.c_str(), -1, SQLITE_TRANSIENT), db, "bind SQLite text");
}

void bind_optional_i64(sqlite3* db, sqlite3_stmt* statement, int index,
                       const std::optional<std::int64_t>& value) {
    if (value) {
        check(sqlite3_bind_int64(statement, index, *value), db, "bind SQLite integer");
    } else {
        check(sqlite3_bind_null(statement, index), db, "bind SQLite null");
    }
}

WorkRecord read_work(sqlite3_stmt* statement) {
    return WorkRecord{
        column_text(statement, 0),
        column_text(statement, 1),
        column_text(statement, 2),
        column_text(statement, 3),
        column_text(statement, 4),
        column_text(statement, 5),
        column_text(statement, 6),
        column_text(statement, 7),
        column_text(statement, 8),
        sqlite3_column_int64(statement, 9),
        sqlite3_column_int64(statement, 10),
        sqlite3_column_int64(statement, 11),
        column_optional_i64(statement, 12),
        column_optional_i64(statement, 13),
        sqlite3_column_int(statement, 14),
        sqlite3_column_int64(statement, 15),
        sqlite3_column_int(statement, 16),
        column_text(statement, 17),
        column_text(statement, 18),
        sqlite3_column_int(statement, 19) != 0,
        sqlite3_column_int(statement, 20) != 0,
        column_text(statement, 21),
        column_text(statement, 22),
        column_text(statement, 23),
    };
}

constexpr const char* kWorkColumns =
    "id,work_type,state,effort,urgency,required_capabilities,eligible_engine_ids,"
    "exact_engine_id,exact_model_id,created_at_ms,eligible_at_ms,next_attempt_at_ms,"
    "deadline_ms,timeout_ms,max_attempts,retry_delay_ms,attempt_count,input_path,result_path,"
    "acknowledged,cancel_requested,selected_engine_id,selected_model_id,technical_failure";

bool retry_permitted(const WorkRecord& work, std::int64_t now_ms) {
    if (work.attempt_count >= work.max_attempts) {
        return false;
    }
    const auto retry_at = now_ms + work.retry_delay_ms;
    return !work.deadline_ms || retry_at < *work.deadline_ms;
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
    effort TEXT NOT NULL CHECK(effort IN ('STANDARD','HIGH')),
    urgency TEXT NOT NULL CHECK(urgency IN ('INTERACTIVE','NORMAL','BACKGROUND')),
    required_capabilities TEXT NOT NULL,
    eligible_engine_ids TEXT NOT NULL,
    exact_engine_id TEXT NOT NULL,
    exact_model_id TEXT NOT NULL,
    created_at_ms INTEGER NOT NULL,
    eligible_at_ms INTEGER NOT NULL,
    next_attempt_at_ms INTEGER NOT NULL,
    deadline_ms INTEGER,
    timeout_ms INTEGER,
    max_attempts INTEGER NOT NULL CHECK(max_attempts >= 1),
    retry_delay_ms INTEGER NOT NULL CHECK(retry_delay_ms >= 0),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    input_path TEXT NOT NULL,
    result_path TEXT NOT NULL,
    acknowledged INTEGER NOT NULL DEFAULT 0,
    cancel_requested INTEGER NOT NULL DEFAULT 0,
    selected_engine_id TEXT NOT NULL DEFAULT '',
    selected_model_id TEXT NOT NULL DEFAULT '',
    technical_failure TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS attempts (
    work_id TEXT NOT NULL REFERENCES work(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    engine_id TEXT NOT NULL,
    model_id TEXT NOT NULL,
    started_at_ms INTEGER NOT NULL,
    ended_at_ms INTEGER,
    state TEXT NOT NULL CHECK(state IN ('RUNNING','SUCCEEDED','FAILED','INTERRUPTED','TIMED_OUT','CANCELLED')),
    technical_failure TEXT NOT NULL DEFAULT '',
    PRIMARY KEY(work_id, attempt_number)
);
CREATE INDEX IF NOT EXISTS work_queue_idx
    ON work(state, eligible_at_ms, next_attempt_at_ms, urgency, created_at_ms, id);
CREATE INDEX IF NOT EXISTS attempts_work_idx ON attempts(work_id, attempt_number);
)SQL";
    check(sqlite3_exec(db_, schema, nullptr, nullptr, nullptr), db_, "initialize C2 Work schema");
}

WorkStore::~WorkStore() {
    if (db_ != nullptr) {
        sqlite3_close(db_);
    }
}

void WorkStore::submit(const WorkRecord& work) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, R"SQL(
INSERT INTO work(
    id,work_type,state,effort,urgency,required_capabilities,eligible_engine_ids,
    exact_engine_id,exact_model_id,created_at_ms,eligible_at_ms,next_attempt_at_ms,
    deadline_ms,timeout_ms,max_attempts,retry_delay_ms,attempt_count,input_path,result_path,
    acknowledged,cancel_requested,selected_engine_id,selected_model_id,technical_failure)
VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?,0,0,'','','')
)SQL");
    int i = 1;
    bind_text(db_, statement.get(), i++, work.id);
    bind_text(db_, statement.get(), i++, work.work_type);
    bind_text(db_, statement.get(), i++, work.state);
    bind_text(db_, statement.get(), i++, work.effort);
    bind_text(db_, statement.get(), i++, work.urgency);
    bind_text(db_, statement.get(), i++, work.required_capabilities);
    bind_text(db_, statement.get(), i++, work.eligible_engine_ids);
    bind_text(db_, statement.get(), i++, work.exact_engine_id);
    bind_text(db_, statement.get(), i++, work.exact_model_id);
    check(sqlite3_bind_int64(statement.get(), i++, work.created_at_ms), db_, "bind creation time");
    check(sqlite3_bind_int64(statement.get(), i++, work.eligible_at_ms), db_, "bind eligible time");
    check(sqlite3_bind_int64(statement.get(), i++, work.next_attempt_at_ms), db_, "bind next attempt time");
    bind_optional_i64(db_, statement.get(), i++, work.deadline_ms);
    bind_optional_i64(db_, statement.get(), i++, work.timeout_ms);
    check(sqlite3_bind_int(statement.get(), i++, work.max_attempts), db_, "bind max attempts");
    check(sqlite3_bind_int64(statement.get(), i++, work.retry_delay_ms), db_, "bind retry delay");
    bind_text(db_, statement.get(), i++, work.input_path.string());
    bind_text(db_, statement.get(), i++, work.result_path.string());
    check(sqlite3_step(statement.get()), db_, "insert Work");
}

std::optional<WorkRecord> WorkStore::find(const std::string& id) {
    std::lock_guard lock(mutex_);
    const std::string sql = std::string("SELECT ") + kWorkColumns + " FROM work WHERE id=?";
    Statement statement(db_, sql.c_str());
    bind_text(db_, statement.get(), 1, id);
    const auto rc = sqlite3_step(statement.get());
    if (rc == SQLITE_DONE) {
        return std::nullopt;
    }
    check(rc, db_, "read Work");
    return read_work(statement.get());
}

std::optional<WorkRecord> WorkStore::next_eligible(std::int64_t now_ms) {
    std::lock_guard lock(mutex_);
    const std::string sql = std::string("SELECT ") + kWorkColumns + R"SQL(
 FROM work
 WHERE state='QUEUED' AND cancel_requested=0 AND eligible_at_ms<=? AND next_attempt_at_ms<=?
 ORDER BY CASE urgency WHEN 'INTERACTIVE' THEN 0 WHEN 'NORMAL' THEN 1 ELSE 2 END,
          created_at_ms,rowid
 LIMIT 1
)SQL";
    Statement statement(db_, sql.c_str());
    check(sqlite3_bind_int64(statement.get(), 1, now_ms), db_, "bind scheduler time");
    check(sqlite3_bind_int64(statement.get(), 2, now_ms), db_, "bind retry time");
    const auto rc = sqlite3_step(statement.get());
    if (rc == SQLITE_DONE) {
        return std::nullopt;
    }
    check(rc, db_, "read eligible Work");
    return read_work(statement.get());
}

void WorkStore::expire_queued_deadlines(std::int64_t now_ms) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, R"SQL(
UPDATE work
SET state='FAILED',technical_failure='DEADLINE_EXPIRED: Work deadline expired before dispatch'
WHERE state='QUEUED' AND deadline_ms IS NOT NULL AND deadline_ms<=?
)SQL");
    check(sqlite3_bind_int64(statement.get(), 1, now_ms), db_, "bind deadline sweep time");
    check(sqlite3_step(statement.get()), db_, "expire queued Work deadlines");
}

void WorkStore::fail_queued(const std::string& id, const std::string& technical_failure) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, "UPDATE work SET state='FAILED',technical_failure=? WHERE id=? AND state='QUEUED'");
    bind_text(db_, statement.get(), 1, technical_failure);
    bind_text(db_, statement.get(), 2, id);
    check(sqlite3_step(statement.get()), db_, "fail queued Work");
}

int WorkStore::begin_attempt(const std::string& id, const std::string& engine_id,
                             const std::string& model_id, std::int64_t started_at_ms) {
    std::lock_guard lock(mutex_);
    check(sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr), db_, "begin attempt transaction");
    try {
        Statement find(db_, "SELECT attempt_count FROM work WHERE id=? AND state='QUEUED' AND cancel_requested=0");
        bind_text(db_, find.get(), 1, id);
        const auto rc = sqlite3_step(find.get());
        if (rc == SQLITE_DONE) {
            check(sqlite3_exec(db_, "ROLLBACK", nullptr, nullptr, nullptr), db_, "rollback absent attempt");
            return 0;
        }
        check(rc, db_, "read attempt count");
        const int attempt_number = sqlite3_column_int(find.get(), 0) + 1;

        Statement update(db_, R"SQL(
UPDATE work
SET state='RUNNING',attempt_count=?,selected_engine_id=?,selected_model_id=?,technical_failure=''
WHERE id=? AND state='QUEUED' AND cancel_requested=0
)SQL");
        check(sqlite3_bind_int(update.get(), 1, attempt_number), db_, "bind attempt number");
        bind_text(db_, update.get(), 2, engine_id);
        bind_text(db_, update.get(), 3, model_id);
        bind_text(db_, update.get(), 4, id);
        check(sqlite3_step(update.get()), db_, "mark Work running");
        if (sqlite3_changes(db_) != 1) {
            check(sqlite3_exec(db_, "ROLLBACK", nullptr, nullptr, nullptr), db_, "rollback lost attempt race");
            return 0;
        }

        Statement insert(db_, R"SQL(
INSERT INTO attempts(work_id,attempt_number,engine_id,model_id,started_at_ms,state)
VALUES(?,?,?,?,?,'RUNNING')
)SQL");
        bind_text(db_, insert.get(), 1, id);
        check(sqlite3_bind_int(insert.get(), 2, attempt_number), db_, "bind attempt number");
        bind_text(db_, insert.get(), 3, engine_id);
        bind_text(db_, insert.get(), 4, model_id);
        check(sqlite3_bind_int64(insert.get(), 5, started_at_ms), db_, "bind attempt start");
        check(sqlite3_step(insert.get()), db_, "insert attempt");
        check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit attempt transaction");
        return attempt_number;
    } catch (...) {
        sqlite3_exec(db_, "ROLLBACK", nullptr, nullptr, nullptr);
        throw;
    }
}

void WorkStore::finish_success(const std::string& id, int attempt_number, std::int64_t ended_at_ms) {
    std::lock_guard lock(mutex_);
    check(sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr), db_, "begin success transaction");
    try {
        Statement attempt(db_, R"SQL(
UPDATE attempts SET state='SUCCEEDED',ended_at_ms=?,technical_failure=''
WHERE work_id=? AND attempt_number=? AND state='RUNNING'
)SQL");
        check(sqlite3_bind_int64(attempt.get(), 1, ended_at_ms), db_, "bind attempt end");
        bind_text(db_, attempt.get(), 2, id);
        check(sqlite3_bind_int(attempt.get(), 3, attempt_number), db_, "bind attempt number");
        check(sqlite3_step(attempt.get()), db_, "mark attempt succeeded");

        Statement work(db_, "UPDATE work SET state='SUCCEEDED',technical_failure='' WHERE id=? AND state='RUNNING'");
        bind_text(db_, work.get(), 1, id);
        check(sqlite3_step(work.get()), db_, "mark Work succeeded");
        check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit success transaction");
    } catch (...) {
        sqlite3_exec(db_, "ROLLBACK", nullptr, nullptr, nullptr);
        throw;
    }
}

void WorkStore::finish_cancelled(const std::string& id, int attempt_number, std::int64_t ended_at_ms) {
    std::lock_guard lock(mutex_);
    check(sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr), db_, "begin cancel transaction");
    try {
        Statement attempt(db_, R"SQL(
UPDATE attempts SET state='CANCELLED',ended_at_ms=?,technical_failure='cancelled'
WHERE work_id=? AND attempt_number=? AND state='RUNNING'
)SQL");
        check(sqlite3_bind_int64(attempt.get(), 1, ended_at_ms), db_, "bind cancellation time");
        bind_text(db_, attempt.get(), 2, id);
        check(sqlite3_bind_int(attempt.get(), 3, attempt_number), db_, "bind attempt number");
        check(sqlite3_step(attempt.get()), db_, "cancel attempt");
        Statement work(db_, "UPDATE work SET state='CANCELLED',technical_failure='cancelled' WHERE id=? AND state='RUNNING'");
        bind_text(db_, work.get(), 1, id);
        check(sqlite3_step(work.get()), db_, "cancel Work");
        check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit cancel transaction");
    } catch (...) {
        sqlite3_exec(db_, "ROLLBACK", nullptr, nullptr, nullptr);
        throw;
    }
}

void WorkStore::finish_retryable_failure(const std::string& id, int attempt_number,
                                         const std::string& attempt_state,
                                         const std::string& technical_failure,
                                         std::int64_t ended_at_ms) {
    if (attempt_state != "FAILED" && attempt_state != "TIMED_OUT") {
        throw std::invalid_argument("retryable attempt state must be FAILED or TIMED_OUT");
    }
    std::lock_guard lock(mutex_);
    check(sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr), db_, "begin failure transaction");
    try {
        Statement read(db_, (std::string("SELECT ") + kWorkColumns + " FROM work WHERE id=? AND state='RUNNING'").c_str());
        bind_text(db_, read.get(), 1, id);
        const auto rc = sqlite3_step(read.get());
        if (rc == SQLITE_DONE) {
            check(sqlite3_exec(db_, "ROLLBACK", nullptr, nullptr, nullptr), db_, "rollback missing running Work");
            return;
        }
        check(rc, db_, "read running Work");
        const auto work = read_work(read.get());

        Statement attempt(db_, R"SQL(
UPDATE attempts SET state=?,ended_at_ms=?,technical_failure=?
WHERE work_id=? AND attempt_number=? AND state='RUNNING'
)SQL");
        bind_text(db_, attempt.get(), 1, attempt_state);
        check(sqlite3_bind_int64(attempt.get(), 2, ended_at_ms), db_, "bind attempt end");
        bind_text(db_, attempt.get(), 3, technical_failure);
        bind_text(db_, attempt.get(), 4, id);
        check(sqlite3_bind_int(attempt.get(), 5, attempt_number), db_, "bind attempt number");
        check(sqlite3_step(attempt.get()), db_, "finish failed attempt");

        if (retry_permitted(work, ended_at_ms)) {
            Statement queue(db_, R"SQL(
UPDATE work SET state='QUEUED',next_attempt_at_ms=?,technical_failure=?,selected_engine_id='',selected_model_id=''
WHERE id=? AND state='RUNNING'
)SQL");
            check(sqlite3_bind_int64(queue.get(), 1, ended_at_ms + work.retry_delay_ms), db_, "bind retry time");
            bind_text(db_, queue.get(), 2, technical_failure);
            bind_text(db_, queue.get(), 3, id);
            check(sqlite3_step(queue.get()), db_, "queue Work retry");
        } else {
            Statement fail(db_, "UPDATE work SET state='FAILED',technical_failure=? WHERE id=? AND state='RUNNING'");
            bind_text(db_, fail.get(), 1, technical_failure);
            bind_text(db_, fail.get(), 2, id);
            check(sqlite3_step(fail.get()), db_, "fail Work after attempt");
        }
        check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit failure transaction");
    } catch (...) {
        sqlite3_exec(db_, "ROLLBACK", nullptr, nullptr, nullptr);
        throw;
    }
}

void WorkStore::recover_interrupted(std::int64_t now_ms) {
    std::lock_guard lock(mutex_);
    check(sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr), db_, "begin restart recovery");
    try {
        Statement rows(db_, (std::string("SELECT ") + kWorkColumns + " FROM work WHERE state='RUNNING'").c_str());
        std::vector<WorkRecord> running;
        while (true) {
            const auto rc = sqlite3_step(rows.get());
            if (rc == SQLITE_DONE) {
                break;
            }
            check(rc, db_, "read running Work for recovery");
            running.push_back(read_work(rows.get()));
        }
        for (const auto& work : running) {
            Statement interrupt(db_, R"SQL(
UPDATE attempts SET state='INTERRUPTED',ended_at_ms=?,technical_failure='Kernel restarted during active attempt'
WHERE work_id=? AND attempt_number=? AND state='RUNNING'
)SQL");
            check(sqlite3_bind_int64(interrupt.get(), 1, now_ms), db_, "bind interruption time");
            bind_text(db_, interrupt.get(), 2, work.id);
            check(sqlite3_bind_int(interrupt.get(), 3, work.attempt_count), db_, "bind interrupted attempt number");
            check(sqlite3_step(interrupt.get()), db_, "interrupt running attempt");

            if (work.cancel_requested) {
                Statement cancel(db_, R"SQL(
UPDATE work SET state='CANCELLED',technical_failure='cancelled before restart recovery'
WHERE id=? AND state='RUNNING'
)SQL");
                bind_text(db_, cancel.get(), 1, work.id);
                check(sqlite3_step(cancel.get()), db_, "cancel interrupted Work with prior cancellation request");
            } else if (retry_permitted(work, now_ms)) {
                Statement queue(db_, R"SQL(
UPDATE work SET state='QUEUED',next_attempt_at_ms=?,technical_failure='Kernel restarted during active attempt',
                selected_engine_id='',selected_model_id=''
WHERE id=? AND state='RUNNING'
)SQL");
                check(sqlite3_bind_int64(queue.get(), 1, now_ms + work.retry_delay_ms), db_, "bind restart retry time");
                bind_text(db_, queue.get(), 2, work.id);
                check(sqlite3_step(queue.get()), db_, "queue interrupted Work retry");
            } else {
                Statement fail(db_, R"SQL(
UPDATE work SET state='FAILED',technical_failure='Kernel restarted during active attempt'
WHERE id=? AND state='RUNNING'
)SQL");
                bind_text(db_, fail.get(), 1, work.id);
                check(sqlite3_step(fail.get()), db_, "fail interrupted Work");
            }
        }
        check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit restart recovery");
    } catch (...) {
        sqlite3_exec(db_, "ROLLBACK", nullptr, nullptr, nullptr);
        throw;
    }
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
        Statement cancel_statement(db_, R"SQL(
UPDATE work SET state='CANCELLED',cancel_requested=1,technical_failure='cancelled'
WHERE id=? AND state='QUEUED'
)SQL");
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
