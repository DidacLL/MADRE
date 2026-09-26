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
    if (sqlite3_column_type(statement, column) == SQLITE_NULL) return std::nullopt;
    return sqlite3_column_int64(statement, column);
}

void bind_text(sqlite3* db, sqlite3_stmt* statement, int index, const std::string& value) {
    check(sqlite3_bind_text(statement, index, value.c_str(), -1, SQLITE_TRANSIENT), db, "bind SQLite text");
}

void bind_optional_i64(sqlite3* db, sqlite3_stmt* statement, int index,
                       const std::optional<std::int64_t>& value) {
    if (value) check(sqlite3_bind_int64(statement, index, *value), db, "bind SQLite integer");
    else check(sqlite3_bind_null(statement, index), db, "bind SQLite null");
}

constexpr const char* kWorkColumns =
    "id,state,urgency,created_at_ms,eligible_at_ms,next_attempt_at_ms,deadline_ms,attempt_timeout_ms,"
    "max_attempts,retry_delay_ms,retry_safety,attempt_count,input_path,result_path,acknowledged,cancel_requested,"
    "selected_candidate_id,selected_target_identity,technical_failure";

WorkRecord read_work_row(sqlite3_stmt* statement) {
    return WorkRecord{
        column_text(statement, 0),
        column_text(statement, 1),
        column_text(statement, 2),
        sqlite3_column_int64(statement, 3),
        sqlite3_column_int64(statement, 4),
        sqlite3_column_int64(statement, 5),
        column_optional_i64(statement, 6),
        column_optional_i64(statement, 7),
        sqlite3_column_int(statement, 8),
        sqlite3_column_int64(statement, 9),
        column_text(statement, 10),
        sqlite3_column_int(statement, 11),
        column_text(statement, 12),
        column_text(statement, 13),
        sqlite3_column_int(statement, 14) != 0,
        sqlite3_column_int(statement, 15) != 0,
        column_text(statement, 16),
        column_text(statement, 17),
        column_text(statement, 18),
        {},
    };
}

std::vector<ProcessInvocationSpec> load_candidates(sqlite3* db, const std::string& work_id) {
    Statement rows(db, R"SQL(
SELECT position,candidate_id,kind,executable,target_identity
FROM candidates WHERE work_id=? ORDER BY position
)SQL");
    bind_text(db, rows.get(), 1, work_id);
    std::vector<ProcessInvocationSpec> candidates;
    while (true) {
        const auto rc = sqlite3_step(rows.get());
        if (rc == SQLITE_DONE) break;
        check(rc, db, "read invocation candidate");
        const int position = sqlite3_column_int(rows.get(), 0);
        const auto kind = column_text(rows.get(), 2);
        if (kind != "PROCESS") throw std::runtime_error("unsupported persisted invocation kind: " + kind);
        ProcessInvocationSpec candidate;
        candidate.candidate_id = column_text(rows.get(), 1);
        candidate.executable = column_text(rows.get(), 3);
        candidate.target_identity = column_text(rows.get(), 4);
        Statement args(db, R"SQL(
SELECT value FROM candidate_args WHERE work_id=? AND position=? ORDER BY arg_index
)SQL");
        bind_text(db, args.get(), 1, work_id);
        check(sqlite3_bind_int(args.get(), 2, position), db, "bind candidate position");
        while (true) {
            const auto arg_rc = sqlite3_step(args.get());
            if (arg_rc == SQLITE_DONE) break;
            check(arg_rc, db, "read invocation argument");
            candidate.arguments.push_back(column_text(args.get(), 0));
        }
        candidates.push_back(std::move(candidate));
    }
    return candidates;
}

bool attempt_budget_remaining(const WorkRecord& work, std::int64_t now_ms) {
    if (work.attempt_count >= work.max_attempts) return false;
    const auto retry_at = now_ms + work.retry_delay_ms;
    return !work.deadline_ms || retry_at < *work.deadline_ms;
}

bool retries_definite_failures(const WorkRecord& work) {
    return work.retry_safety == "DEFINITE_FAILURES" || work.retry_safety == "INCLUDING_UNKNOWN_COMPLETION";
}

bool retries_unknown_completion(const WorkRecord& work) {
    return work.retry_safety == "INCLUDING_UNKNOWN_COMPLETION";
}

bool table_exists(sqlite3* db, const char* table) {
    Statement statement(db, "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?");
    bind_text(db, statement.get(), 1, table);
    return sqlite3_step(statement.get()) == SQLITE_ROW;
}

int user_version(sqlite3* db) {
    Statement statement(db, "PRAGMA user_version");
    check(sqlite3_step(statement.get()), db, "read SQLite user_version");
    return sqlite3_column_int(statement.get(), 0);
}

}  // namespace

WorkStore::WorkStore(const std::filesystem::path& database_path) {
    std::filesystem::create_directories(database_path.parent_path());
    const auto rc = sqlite3_open_v2(database_path.string().c_str(), &db_,
                                    SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX,
                                    nullptr);
    if (rc != SQLITE_OK) {
        const std::string message = db_ == nullptr ? "open SQLite database" : sqlite3_errmsg(db_);
        if (db_ != nullptr) sqlite3_close(db_);
        db_ = nullptr;
        throw std::runtime_error(message);
    }
    sqlite3_busy_timeout(db_, 5000);
    check(sqlite3_exec(db_, "PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;", nullptr, nullptr, nullptr), db_, "initialize SQLite pragmas");

    if (table_exists(db_, "work") && user_version(db_) != 3) {
        throw std::runtime_error("incompatible development Kernel database; remove generated Lane C state before using protocol v3");
    }

    constexpr const char* schema = R"SQL(
CREATE TABLE IF NOT EXISTS work (
    id TEXT PRIMARY KEY,
    state TEXT NOT NULL CHECK(state IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED','UNKNOWN_COMPLETION')),
    urgency TEXT NOT NULL CHECK(urgency IN ('INTERACTIVE','NORMAL','BACKGROUND')),
    created_at_ms INTEGER NOT NULL,
    eligible_at_ms INTEGER NOT NULL,
    next_attempt_at_ms INTEGER NOT NULL,
    deadline_ms INTEGER,
    attempt_timeout_ms INTEGER,
    max_attempts INTEGER NOT NULL CHECK(max_attempts >= 1),
    retry_delay_ms INTEGER NOT NULL CHECK(retry_delay_ms >= 0),
    retry_safety TEXT NOT NULL CHECK(retry_safety IN ('NEVER','DEFINITE_FAILURES','INCLUDING_UNKNOWN_COMPLETION')),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    input_path TEXT NOT NULL,
    result_path TEXT NOT NULL,
    acknowledged INTEGER NOT NULL DEFAULT 0,
    cancel_requested INTEGER NOT NULL DEFAULT 0,
    selected_candidate_id TEXT NOT NULL DEFAULT '',
    selected_target_identity TEXT NOT NULL DEFAULT '',
    technical_failure TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS candidates (
    work_id TEXT NOT NULL REFERENCES work(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    candidate_id TEXT NOT NULL,
    kind TEXT NOT NULL CHECK(kind='PROCESS'),
    executable TEXT NOT NULL,
    target_identity TEXT NOT NULL DEFAULT '',
    PRIMARY KEY(work_id, position),
    UNIQUE(work_id, candidate_id)
);
CREATE TABLE IF NOT EXISTS candidate_args (
    work_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    arg_index INTEGER NOT NULL,
    value TEXT NOT NULL,
    PRIMARY KEY(work_id, position, arg_index),
    FOREIGN KEY(work_id, position) REFERENCES candidates(work_id, position) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS attempts (
    work_id TEXT NOT NULL REFERENCES work(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    candidate_id TEXT NOT NULL,
    target_identity TEXT NOT NULL DEFAULT '',
    started_at_ms INTEGER NOT NULL,
    ended_at_ms INTEGER,
    state TEXT NOT NULL CHECK(state IN ('RUNNING','SUCCEEDED','FAILED','TIMED_OUT','CANCELLED','UNKNOWN_COMPLETION')),
    technical_failure TEXT NOT NULL DEFAULT '',
    exit_code INTEGER,
    PRIMARY KEY(work_id, attempt_number)
);
CREATE INDEX IF NOT EXISTS work_queue_idx
    ON work(state, eligible_at_ms, next_attempt_at_ms, urgency, created_at_ms, id);
CREATE INDEX IF NOT EXISTS attempts_work_idx ON attempts(work_id, attempt_number);
PRAGMA user_version=3;
)SQL";
    check(sqlite3_exec(db_, schema, nullptr, nullptr, nullptr), db_, "initialize LCR1 Work schema");
}

WorkStore::~WorkStore() {
    if (db_ != nullptr) sqlite3_close(db_);
}

void WorkStore::submit(const WorkRecord& work) {
    std::lock_guard lock(mutex_);
    check(sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr), db_, "begin Work submission");
    try {
        Statement statement(db_, R"SQL(
INSERT INTO work(id,state,urgency,created_at_ms,eligible_at_ms,next_attempt_at_ms,deadline_ms,attempt_timeout_ms,
                 max_attempts,retry_delay_ms,retry_safety,attempt_count,input_path,result_path,acknowledged,cancel_requested,
                 selected_candidate_id,selected_target_identity,technical_failure)
VALUES(?,?,?,?,?,?,?,?,?,?,?,0,?,?,0,0,'','','')
)SQL");
        int i = 1;
        bind_text(db_, statement.get(), i++, work.id);
        bind_text(db_, statement.get(), i++, work.state);
        bind_text(db_, statement.get(), i++, work.urgency);
        check(sqlite3_bind_int64(statement.get(), i++, work.created_at_ms), db_, "bind creation time");
        check(sqlite3_bind_int64(statement.get(), i++, work.eligible_at_ms), db_, "bind eligible time");
        check(sqlite3_bind_int64(statement.get(), i++, work.next_attempt_at_ms), db_, "bind next attempt time");
        bind_optional_i64(db_, statement.get(), i++, work.deadline_ms);
        bind_optional_i64(db_, statement.get(), i++, work.attempt_timeout_ms);
        check(sqlite3_bind_int(statement.get(), i++, work.max_attempts), db_, "bind max attempts");
        check(sqlite3_bind_int64(statement.get(), i++, work.retry_delay_ms), db_, "bind retry delay");
        bind_text(db_, statement.get(), i++, work.retry_safety);
        bind_text(db_, statement.get(), i++, work.input_path.string());
        bind_text(db_, statement.get(), i++, work.result_path.string());
        check(sqlite3_step(statement.get()), db_, "insert Work");

        for (std::size_t position = 0; position < work.candidates.size(); ++position) {
            const auto& candidate = work.candidates[position];
            Statement c(db_, R"SQL(
INSERT INTO candidates(work_id,position,candidate_id,kind,executable,target_identity)
VALUES(?,?,?,'PROCESS',?,?)
)SQL");
            bind_text(db_, c.get(), 1, work.id);
            check(sqlite3_bind_int(c.get(), 2, static_cast<int>(position)), db_, "bind candidate position");
            bind_text(db_, c.get(), 3, candidate.candidate_id);
            bind_text(db_, c.get(), 4, candidate.executable.string());
            bind_text(db_, c.get(), 5, candidate.target_identity);
            check(sqlite3_step(c.get()), db_, "insert invocation candidate");
            for (std::size_t arg = 0; arg < candidate.arguments.size(); ++arg) {
                Statement a(db_, "INSERT INTO candidate_args(work_id,position,arg_index,value) VALUES(?,?,?,?)");
                bind_text(db_, a.get(), 1, work.id);
                check(sqlite3_bind_int(a.get(), 2, static_cast<int>(position)), db_, "bind candidate position");
                check(sqlite3_bind_int(a.get(), 3, static_cast<int>(arg)), db_, "bind argument index");
                bind_text(db_, a.get(), 4, candidate.arguments[arg]);
                check(sqlite3_step(a.get()), db_, "insert invocation argument");
            }
        }
        check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit Work submission");
    } catch (...) {
        sqlite3_exec(db_, "ROLLBACK", nullptr, nullptr, nullptr);
        throw;
    }
}

std::optional<WorkRecord> WorkStore::find(const std::string& id) {
    std::lock_guard lock(mutex_);
    const std::string sql = std::string("SELECT ") + kWorkColumns + " FROM work WHERE id=?";
    Statement statement(db_, sql.c_str());
    bind_text(db_, statement.get(), 1, id);
    const auto rc = sqlite3_step(statement.get());
    if (rc == SQLITE_DONE) return std::nullopt;
    check(rc, db_, "read Work");
    auto work = read_work_row(statement.get());
    work.candidates = load_candidates(db_, work.id);
    return work;
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
    if (rc == SQLITE_DONE) return std::nullopt;
    check(rc, db_, "read eligible Work");
    auto work = read_work_row(statement.get());
    work.candidates = load_candidates(db_, work.id);
    return work;
}

void WorkStore::expire_queued_deadlines(std::int64_t now_ms) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, R"SQL(
UPDATE work SET state='FAILED',technical_failure='DEADLINE_EXPIRED: Work deadline expired before dispatch'
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

int WorkStore::begin_attempt(const std::string& id, const ProcessInvocationSpec& candidate,
                             std::int64_t started_at_ms) {
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
UPDATE work SET state='RUNNING',attempt_count=?,selected_candidate_id=?,selected_target_identity=?,technical_failure=''
WHERE id=? AND state='QUEUED' AND cancel_requested=0
)SQL");
        check(sqlite3_bind_int(update.get(), 1, attempt_number), db_, "bind attempt number");
        bind_text(db_, update.get(), 2, candidate.candidate_id);
        bind_text(db_, update.get(), 3, candidate.target_identity);
        bind_text(db_, update.get(), 4, id);
        check(sqlite3_step(update.get()), db_, "mark Work running");
        if (sqlite3_changes(db_) != 1) {
            check(sqlite3_exec(db_, "ROLLBACK", nullptr, nullptr, nullptr), db_, "rollback lost attempt race");
            return 0;
        }
        Statement insert(db_, R"SQL(
INSERT INTO attempts(work_id,attempt_number,candidate_id,target_identity,started_at_ms,state)
VALUES(?,?,?,?,?,'RUNNING')
)SQL");
        bind_text(db_, insert.get(), 1, id);
        check(sqlite3_bind_int(insert.get(), 2, attempt_number), db_, "bind attempt number");
        bind_text(db_, insert.get(), 3, candidate.candidate_id);
        bind_text(db_, insert.get(), 4, candidate.target_identity);
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
        Statement attempt(db_, "UPDATE attempts SET state='SUCCEEDED',ended_at_ms=?,technical_failure='' WHERE work_id=? AND attempt_number=? AND state='RUNNING'");
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
        Statement attempt(db_, "UPDATE attempts SET state='CANCELLED',ended_at_ms=?,technical_failure='PROCESS_CANCELLED' WHERE work_id=? AND attempt_number=? AND state='RUNNING'");
        check(sqlite3_bind_int64(attempt.get(), 1, ended_at_ms), db_, "bind cancellation time");
        bind_text(db_, attempt.get(), 2, id);
        check(sqlite3_bind_int(attempt.get(), 3, attempt_number), db_, "bind attempt number");
        check(sqlite3_step(attempt.get()), db_, "cancel attempt");
        Statement work(db_, "UPDATE work SET state='CANCELLED',technical_failure='PROCESS_CANCELLED' WHERE id=? AND state='RUNNING'");
        bind_text(db_, work.get(), 1, id);
        check(sqlite3_step(work.get()), db_, "cancel Work");
        check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit cancel transaction");
    } catch (...) {
        sqlite3_exec(db_, "ROLLBACK", nullptr, nullptr, nullptr);
        throw;
    }
}

void WorkStore::finish_definite_failure(const std::string& id, int attempt_number,
                                        const std::string& attempt_state,
                                        const std::string& technical_failure,
                                        std::optional<int> exit_code,
                                        std::int64_t ended_at_ms) {
    if (attempt_state != "FAILED" && attempt_state != "TIMED_OUT") {
        throw std::invalid_argument("definite attempt failure state must be FAILED or TIMED_OUT");
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
        const auto work = read_work_row(read.get());
        Statement attempt(db_, R"SQL(
UPDATE attempts SET state=?,ended_at_ms=?,technical_failure=?,exit_code=?
WHERE work_id=? AND attempt_number=? AND state='RUNNING'
)SQL");
        bind_text(db_, attempt.get(), 1, attempt_state);
        check(sqlite3_bind_int64(attempt.get(), 2, ended_at_ms), db_, "bind attempt end");
        bind_text(db_, attempt.get(), 3, technical_failure);
        if (exit_code) check(sqlite3_bind_int(attempt.get(), 4, *exit_code), db_, "bind exit code");
        else check(sqlite3_bind_null(attempt.get(), 4), db_, "bind null exit code");
        bind_text(db_, attempt.get(), 5, id);
        check(sqlite3_bind_int(attempt.get(), 6, attempt_number), db_, "bind attempt number");
        check(sqlite3_step(attempt.get()), db_, "finish failed attempt");

        if (work.cancel_requested) {
            Statement cancel(db_, "UPDATE work SET state='CANCELLED',technical_failure='PROCESS_CANCELLED' WHERE id=? AND state='RUNNING'");
            bind_text(db_, cancel.get(), 1, id);
            check(sqlite3_step(cancel.get()), db_, "cancel Work during failure finalization");
        } else if (retries_definite_failures(work) && attempt_budget_remaining(work, ended_at_ms)) {
            Statement queue(db_, R"SQL(
UPDATE work SET state='QUEUED',next_attempt_at_ms=?,technical_failure=?
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
            if (rc == SQLITE_DONE) break;
            check(rc, db_, "read running Work for recovery");
            running.push_back(read_work_row(rows.get()));
        }
        for (const auto& work : running) {
            const std::string failure = "UNKNOWN_COMPLETION: Kernel lost certainty about an active physical attempt";
            Statement interrupt(db_, R"SQL(
UPDATE attempts SET state='UNKNOWN_COMPLETION',ended_at_ms=?,technical_failure=?
WHERE work_id=? AND attempt_number=? AND state='RUNNING'
)SQL");
            check(sqlite3_bind_int64(interrupt.get(), 1, now_ms), db_, "bind interruption time");
            bind_text(db_, interrupt.get(), 2, failure);
            bind_text(db_, interrupt.get(), 3, work.id);
            check(sqlite3_bind_int(interrupt.get(), 4, work.attempt_count), db_, "bind interrupted attempt number");
            check(sqlite3_step(interrupt.get()), db_, "mark attempt completion unknown");

            if (!work.cancel_requested && retries_unknown_completion(work) && attempt_budget_remaining(work, now_ms)) {
                Statement queue(db_, R"SQL(
UPDATE work SET state='QUEUED',next_attempt_at_ms=?,technical_failure=?
WHERE id=? AND state='RUNNING'
)SQL");
                check(sqlite3_bind_int64(queue.get(), 1, now_ms + work.retry_delay_ms), db_, "bind restart retry time");
                bind_text(db_, queue.get(), 2, failure);
                bind_text(db_, queue.get(), 3, work.id);
                check(sqlite3_step(queue.get()), db_, "queue explicitly safe unknown-completion retry");
            } else {
                Statement unknown(db_, "UPDATE work SET state='UNKNOWN_COMPLETION',technical_failure=? WHERE id=? AND state='RUNNING'");
                bind_text(db_, unknown.get(), 1, failure);
                bind_text(db_, unknown.get(), 2, work.id);
                check(sqlite3_step(unknown.get()), db_, "retain unknown completion honestly");
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
    if (rc == SQLITE_DONE) return {};
    check(rc, db_, "read Work for cancellation");
    const auto state = column_text(find_statement.get(), 0);
    if (state == "QUEUED") {
        Statement cancel_statement(db_, "UPDATE work SET state='CANCELLED',cancel_requested=1,technical_failure='cancelled before dispatch' WHERE id=? AND state='QUEUED'");
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
    if (rc == SQLITE_DONE) return false;
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
