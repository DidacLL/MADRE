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
    sqlite3* db_{};
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

std::optional<int> column_optional_int(sqlite3_stmt* statement, int column) {
    if (sqlite3_column_type(statement, column) == SQLITE_NULL) return std::nullopt;
    return sqlite3_column_int(statement, column);
}

void bind_text(sqlite3* db, sqlite3_stmt* statement, int index, const std::string& value) {
    check(sqlite3_bind_text(statement, index, value.c_str(), -1, SQLITE_TRANSIENT), db, "bind SQLite text");
}

void bind_optional_i64(sqlite3* db, sqlite3_stmt* statement, int index, const std::optional<std::int64_t>& value) {
    if (value) check(sqlite3_bind_int64(statement, index, *value), db, "bind SQLite integer");
    else check(sqlite3_bind_null(statement, index), db, "bind SQLite null");
}

void bind_optional_int(sqlite3* db, sqlite3_stmt* statement, int index, const std::optional<int>& value) {
    if (value) check(sqlite3_bind_int(statement, index, *value), db, "bind SQLite integer");
    else check(sqlite3_bind_null(statement, index), db, "bind SQLite null");
}

constexpr const char* kWorkColumns =
    "id,state,urgency,created_at_ms,eligible_at_ms,next_attempt_at_ms,deadline_ms,attempt_timeout_ms,"
    "max_attempts,retry_delay_ms,retry_safety,attempt_count,result_path,released,cancel_requested,technical_failure";

WorkRecord read_work_row(sqlite3_stmt* statement) {
    return WorkRecord{
        column_text(statement, 0), column_text(statement, 1), column_text(statement, 2),
        sqlite3_column_int64(statement, 3), sqlite3_column_int64(statement, 4), sqlite3_column_int64(statement, 5),
        column_optional_i64(statement, 6), column_optional_i64(statement, 7), sqlite3_column_int(statement, 8),
        sqlite3_column_int64(statement, 9), column_text(statement, 10), sqlite3_column_int(statement, 11),
        column_text(statement, 12), sqlite3_column_int(statement, 13) != 0, sqlite3_column_int(statement, 14) != 0,
        column_text(statement, 15), {}, std::nullopt,
    };
}

std::vector<ConcretePhysicalInvocationSpec> load_candidates(sqlite3* db, const std::string& work_id) {
    Statement rows(db, R"SQL(
SELECT position,candidate_id,kind,payload_path,target_identity,executable,uri
FROM candidates WHERE work_id=? ORDER BY position
)SQL");
    bind_text(db, rows.get(), 1, work_id);
    std::vector<ConcretePhysicalInvocationSpec> candidates;
    while (true) {
        const auto rc = sqlite3_step(rows.get());
        if (rc == SQLITE_DONE) break;
        check(rc, db, "read invocation candidate");
        const int position = sqlite3_column_int(rows.get(), 0);
        const auto candidate_id = column_text(rows.get(), 1);
        const auto kind = column_text(rows.get(), 2);
        const auto payload = std::filesystem::path(column_text(rows.get(), 3));
        const auto target = column_text(rows.get(), 4);
        if (kind == "PROCESS") {
            ProcessInvocationSpec candidate;
            candidate.candidate_id = candidate_id;
            candidate.payload_path = payload;
            candidate.target_identity = target;
            candidate.executable = column_text(rows.get(), 5);
            Statement args(db, "SELECT value FROM candidate_args WHERE work_id=? AND position=? ORDER BY arg_index");
            bind_text(db, args.get(), 1, work_id);
            check(sqlite3_bind_int(args.get(), 2, position), db, "bind candidate position");
            while (true) {
                const auto arg_rc = sqlite3_step(args.get());
                if (arg_rc == SQLITE_DONE) break;
                check(arg_rc, db, "read invocation argument");
                candidate.arguments.push_back(column_text(args.get(), 0));
            }
            candidates.emplace_back(std::move(candidate));
        } else if (kind == "HTTP") {
            HttpInvocationSpec candidate;
            candidate.candidate_id = candidate_id;
            candidate.payload_path = payload;
            candidate.target_identity = target;
            candidate.uri = column_text(rows.get(), 6);
            Statement headers(db, R"SQL(
SELECT name,source,literal_value,environment_variable,prefix,suffix
FROM candidate_headers WHERE work_id=? AND position=? ORDER BY header_index
)SQL");
            bind_text(db, headers.get(), 1, work_id);
            check(sqlite3_bind_int(headers.get(), 2, position), db, "bind header candidate position");
            while (true) {
                const auto header_rc = sqlite3_step(headers.get());
                if (header_rc == SQLITE_DONE) break;
                check(header_rc, db, "read HTTP header");
                HttpHeaderSpec header;
                header.name = column_text(headers.get(), 0);
                const auto source = column_text(headers.get(), 1);
                header.source = source == "ENVIRONMENT" ? HttpHeaderSource::Environment : HttpHeaderSource::Literal;
                header.literal_value = column_text(headers.get(), 2);
                header.environment_variable = column_text(headers.get(), 3);
                header.prefix = column_text(headers.get(), 4);
                header.suffix = column_text(headers.get(), 5);
                candidate.headers.push_back(std::move(header));
            }
            candidates.emplace_back(std::move(candidate));
        } else {
            throw std::runtime_error("unsupported persisted invocation kind: " + kind);
        }
    }
    return candidates;
}

std::optional<AttemptRecord> load_latest_attempt(sqlite3* db, const std::string& work_id) {
    Statement row(db, R"SQL(
SELECT attempt_number,candidate_id,kind,target_identity,started_at_ms,ended_at_ms,state,technical_failure,exit_code,http_status
FROM attempts WHERE work_id=? ORDER BY attempt_number DESC LIMIT 1
)SQL");
    bind_text(db, row.get(), 1, work_id);
    const auto rc = sqlite3_step(row.get());
    if (rc == SQLITE_DONE) return std::nullopt;
    check(rc, db, "read latest attempt");
    return AttemptRecord{
        sqlite3_column_int(row.get(), 0), column_text(row.get(), 1), column_text(row.get(), 2), column_text(row.get(), 3),
        sqlite3_column_int64(row.get(), 4), column_optional_i64(row.get(), 5), column_text(row.get(), 6), column_text(row.get(), 7),
        column_optional_int(row.get(), 8), column_optional_int(row.get(), 9),
    };
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

void rollback(sqlite3* db) noexcept { sqlite3_exec(db, "ROLLBACK", nullptr, nullptr, nullptr); }

}  // namespace

WorkStore::WorkStore(const std::filesystem::path& database_path) {
    std::filesystem::create_directories(database_path.parent_path());
    const auto rc = sqlite3_open_v2(database_path.string().c_str(), &db_, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nullptr);
    if (rc != SQLITE_OK) {
        const std::string message = db_ == nullptr ? "open SQLite database" : sqlite3_errmsg(db_);
        if (db_ != nullptr) sqlite3_close(db_);
        db_ = nullptr;
        throw std::runtime_error(message);
    }
    sqlite3_busy_timeout(db_, 5000);
    check(sqlite3_exec(db_, "PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;", nullptr, nullptr, nullptr), db_, "initialize SQLite pragmas");
    if (table_exists(db_, "work") && user_version(db_) != 4) {
        throw std::runtime_error("incompatible development Kernel database; remove generated Lane C state before using protocol v4");
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
    result_path TEXT NOT NULL,
    released INTEGER NOT NULL DEFAULT 0,
    cancel_requested INTEGER NOT NULL DEFAULT 0,
    technical_failure TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS candidates (
    work_id TEXT NOT NULL REFERENCES work(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    candidate_id TEXT NOT NULL,
    kind TEXT NOT NULL CHECK(kind IN ('PROCESS','HTTP')),
    payload_path TEXT NOT NULL,
    target_identity TEXT NOT NULL DEFAULT '',
    executable TEXT NOT NULL DEFAULT '',
    uri TEXT NOT NULL DEFAULT '',
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
CREATE TABLE IF NOT EXISTS candidate_headers (
    work_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    header_index INTEGER NOT NULL,
    name TEXT NOT NULL,
    source TEXT NOT NULL CHECK(source IN ('LITERAL','ENVIRONMENT')),
    literal_value TEXT NOT NULL DEFAULT '',
    environment_variable TEXT NOT NULL DEFAULT '',
    prefix TEXT NOT NULL DEFAULT '',
    suffix TEXT NOT NULL DEFAULT '',
    PRIMARY KEY(work_id, position,header_index),
    FOREIGN KEY(work_id, position) REFERENCES candidates(work_id, position) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS attempts (
    work_id TEXT NOT NULL REFERENCES work(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    candidate_id TEXT NOT NULL,
    kind TEXT NOT NULL CHECK(kind IN ('PROCESS','HTTP')),
    target_identity TEXT NOT NULL DEFAULT '',
    started_at_ms INTEGER NOT NULL,
    ended_at_ms INTEGER,
    state TEXT NOT NULL CHECK(state IN ('RUNNING','SUCCEEDED','FAILED','TIMED_OUT','CANCELLED','UNKNOWN_COMPLETION')),
    technical_failure TEXT NOT NULL DEFAULT '',
    exit_code INTEGER,
    http_status INTEGER,
    PRIMARY KEY(work_id, attempt_number)
);
CREATE INDEX IF NOT EXISTS work_queue_idx ON work(state,eligible_at_ms,next_attempt_at_ms,urgency,created_at_ms,id);
CREATE INDEX IF NOT EXISTS attempts_work_idx ON attempts(work_id,attempt_number);
PRAGMA user_version=4;
)SQL";
    check(sqlite3_exec(db_, schema, nullptr, nullptr, nullptr), db_, "initialize LCR2 Work schema");
}

WorkStore::~WorkStore() { if (db_ != nullptr) sqlite3_close(db_); }

void WorkStore::submit(const WorkRecord& work) {
    std::lock_guard lock(mutex_);
    check(sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr), db_, "begin Work submission");
    try {
        Statement statement(db_, R"SQL(
INSERT INTO work(id,state,urgency,created_at_ms,eligible_at_ms,next_attempt_at_ms,deadline_ms,attempt_timeout_ms,
                 max_attempts,retry_delay_ms,retry_safety,attempt_count,result_path,released,cancel_requested,technical_failure)
VALUES(?,?,?,?,?,?,?,?,?,?,?,0,?,0,0,'')
)SQL");
        int i = 1;
        bind_text(db_, statement.get(), i++, work.id); bind_text(db_, statement.get(), i++, work.state); bind_text(db_, statement.get(), i++, work.urgency);
        check(sqlite3_bind_int64(statement.get(), i++, work.created_at_ms), db_, "bind creation time");
        check(sqlite3_bind_int64(statement.get(), i++, work.eligible_at_ms), db_, "bind eligible time");
        check(sqlite3_bind_int64(statement.get(), i++, work.next_attempt_at_ms), db_, "bind next attempt time");
        bind_optional_i64(db_, statement.get(), i++, work.deadline_ms); bind_optional_i64(db_, statement.get(), i++, work.attempt_timeout_ms);
        check(sqlite3_bind_int(statement.get(), i++, work.max_attempts), db_, "bind max attempts");
        check(sqlite3_bind_int64(statement.get(), i++, work.retry_delay_ms), db_, "bind retry delay");
        bind_text(db_, statement.get(), i++, work.retry_safety); bind_text(db_, statement.get(), i++, work.result_path.string());
        check(sqlite3_step(statement.get()), db_, "insert Work");

        for (std::size_t position = 0; position < work.candidates.size(); ++position) {
            const auto& candidate = work.candidates[position];
            Statement c(db_, R"SQL(
INSERT INTO candidates(work_id,position,candidate_id,kind,payload_path,target_identity,executable,uri)
VALUES(?,?,?,?,?,?,?,?)
)SQL");
            bind_text(db_, c.get(), 1, work.id); check(sqlite3_bind_int(c.get(), 2, static_cast<int>(position)), db_, "bind candidate position");
            bind_text(db_, c.get(), 3, invocation_id(candidate)); bind_text(db_, c.get(), 4, invocation_kind(candidate));
            bind_text(db_, c.get(), 5, payload_path(candidate).string()); bind_text(db_, c.get(), 6, target_identity(candidate));
            if (const auto* process = std::get_if<ProcessInvocationSpec>(&candidate)) {
                bind_text(db_, c.get(), 7, process->executable.string()); bind_text(db_, c.get(), 8, "");
            } else {
                const auto& http = std::get<HttpInvocationSpec>(candidate);
                bind_text(db_, c.get(), 7, ""); bind_text(db_, c.get(), 8, http.uri);
            }
            check(sqlite3_step(c.get()), db_, "insert invocation candidate");

            if (const auto* process = std::get_if<ProcessInvocationSpec>(&candidate)) {
                for (std::size_t arg = 0; arg < process->arguments.size(); ++arg) {
                    Statement a(db_, "INSERT INTO candidate_args(work_id,position,arg_index,value) VALUES(?,?,?,?)");
                    bind_text(db_, a.get(), 1, work.id); check(sqlite3_bind_int(a.get(), 2, static_cast<int>(position)), db_, "bind candidate position");
                    check(sqlite3_bind_int(a.get(), 3, static_cast<int>(arg)), db_, "bind argument index"); bind_text(db_, a.get(), 4, process->arguments[arg]);
                    check(sqlite3_step(a.get()), db_, "insert invocation argument");
                }
            } else {
                const auto& http = std::get<HttpInvocationSpec>(candidate);
                for (std::size_t header_index = 0; header_index < http.headers.size(); ++header_index) {
                    const auto& header = http.headers[header_index];
                    Statement h(db_, R"SQL(
INSERT INTO candidate_headers(work_id,position,header_index,name,source,literal_value,environment_variable,prefix,suffix)
VALUES(?,?,?,?,?,?,?,?,?)
)SQL");
                    bind_text(db_, h.get(), 1, work.id); check(sqlite3_bind_int(h.get(), 2, static_cast<int>(position)), db_, "bind candidate position");
                    check(sqlite3_bind_int(h.get(), 3, static_cast<int>(header_index)), db_, "bind header index"); bind_text(db_, h.get(), 4, header.name);
                    bind_text(db_, h.get(), 5, header.source == HttpHeaderSource::Literal ? "LITERAL" : "ENVIRONMENT"); bind_text(db_, h.get(), 6, header.literal_value);
                    bind_text(db_, h.get(), 7, header.environment_variable); bind_text(db_, h.get(), 8, header.prefix); bind_text(db_, h.get(), 9, header.suffix);
                    check(sqlite3_step(h.get()), db_, "insert HTTP header");
                }
            }
        }
        check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit Work submission");
    } catch (...) { rollback(db_); throw; }
}

std::optional<WorkRecord> WorkStore::find(const std::string& id) {
    std::lock_guard lock(mutex_);
    const std::string sql = std::string("SELECT ") + kWorkColumns + " FROM work WHERE id=?";
    Statement statement(db_, sql.c_str()); bind_text(db_, statement.get(), 1, id);
    const auto rc = sqlite3_step(statement.get()); if (rc == SQLITE_DONE) return std::nullopt; check(rc, db_, "read Work");
    auto work = read_work_row(statement.get()); work.candidates = load_candidates(db_, work.id); work.latest_attempt = load_latest_attempt(db_, work.id); return work;
}

std::optional<WorkRecord> WorkStore::next_eligible(std::int64_t now_ms) {
    std::lock_guard lock(mutex_);
    const std::string sql = std::string("SELECT ") + kWorkColumns + R"SQL(
 FROM work WHERE state='QUEUED' AND cancel_requested=0 AND eligible_at_ms<=? AND next_attempt_at_ms<=?
 ORDER BY CASE urgency WHEN 'INTERACTIVE' THEN 0 WHEN 'NORMAL' THEN 1 ELSE 2 END,created_at_ms,rowid LIMIT 1
)SQL";
    Statement statement(db_, sql.c_str()); check(sqlite3_bind_int64(statement.get(), 1, now_ms), db_, "bind scheduler time"); check(sqlite3_bind_int64(statement.get(), 2, now_ms), db_, "bind retry time");
    const auto rc = sqlite3_step(statement.get()); if (rc == SQLITE_DONE) return std::nullopt; check(rc, db_, "read eligible Work");
    auto work = read_work_row(statement.get()); work.candidates = load_candidates(db_, work.id); return work;
}

void WorkStore::expire_queued_deadlines(std::int64_t now_ms) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, "UPDATE work SET state='FAILED',technical_failure='DEADLINE_EXPIRED: Work deadline expired before dispatch' WHERE state='QUEUED' AND deadline_ms IS NOT NULL AND deadline_ms<=?");
    check(sqlite3_bind_int64(statement.get(), 1, now_ms), db_, "bind deadline sweep time"); check(sqlite3_step(statement.get()), db_, "expire queued Work deadlines");
}

void WorkStore::fail_queued(const std::string& id, const std::string& technical_failure) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, "UPDATE work SET state='FAILED',technical_failure=? WHERE id=? AND state='QUEUED'");
    bind_text(db_, statement.get(), 1, technical_failure); bind_text(db_, statement.get(), 2, id); check(sqlite3_step(statement.get()), db_, "fail queued Work");
}

int WorkStore::begin_attempt(const std::string& id, const ConcretePhysicalInvocationSpec& candidate, std::int64_t started_at_ms) {
    std::lock_guard lock(mutex_);
    check(sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr), db_, "begin attempt transaction");
    try {
        Statement find(db_, "SELECT attempt_count FROM work WHERE id=? AND state='QUEUED' AND cancel_requested=0"); bind_text(db_, find.get(), 1, id);
        const auto rc = sqlite3_step(find.get()); if (rc == SQLITE_DONE) { rollback(db_); return 0; } check(rc, db_, "read attempt count");
        const int attempt_number = sqlite3_column_int(find.get(), 0) + 1;
        Statement update(db_, "UPDATE work SET state='RUNNING',attempt_count=?,technical_failure='' WHERE id=? AND state='QUEUED' AND cancel_requested=0");
        check(sqlite3_bind_int(update.get(), 1, attempt_number), db_, "bind attempt number"); bind_text(db_, update.get(), 2, id); check(sqlite3_step(update.get()), db_, "mark Work running");
        if (sqlite3_changes(db_) != 1) { rollback(db_); return 0; }
        Statement insert(db_, R"SQL(
INSERT INTO attempts(work_id,attempt_number,candidate_id,kind,target_identity,started_at_ms,state)
VALUES(?,?,?,?,?,?,'RUNNING')
)SQL");
        bind_text(db_, insert.get(), 1, id); check(sqlite3_bind_int(insert.get(), 2, attempt_number), db_, "bind attempt number"); bind_text(db_, insert.get(), 3, invocation_id(candidate));
        bind_text(db_, insert.get(), 4, invocation_kind(candidate)); bind_text(db_, insert.get(), 5, target_identity(candidate)); check(sqlite3_bind_int64(insert.get(), 6, started_at_ms), db_, "bind attempt start");
        check(sqlite3_step(insert.get()), db_, "insert attempt"); check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit attempt transaction"); return attempt_number;
    } catch (...) { rollback(db_); throw; }
}

void WorkStore::finish_success(const std::string& id, int attempt_number, std::optional<int> exit_code, std::optional<int> http_status, std::int64_t ended_at_ms) {
    std::lock_guard lock(mutex_); check(sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr), db_, "begin success transaction");
    try {
        Statement attempt(db_, "UPDATE attempts SET state='SUCCEEDED',ended_at_ms=?,technical_failure='',exit_code=?,http_status=? WHERE work_id=? AND attempt_number=? AND state='RUNNING'");
        check(sqlite3_bind_int64(attempt.get(), 1, ended_at_ms), db_, "bind attempt end"); bind_optional_int(db_, attempt.get(), 2, exit_code); bind_optional_int(db_, attempt.get(), 3, http_status); bind_text(db_, attempt.get(), 4, id);
        check(sqlite3_bind_int(attempt.get(), 5, attempt_number), db_, "bind attempt number"); check(sqlite3_step(attempt.get()), db_, "mark attempt succeeded");
        Statement work(db_, "UPDATE work SET state='SUCCEEDED',technical_failure='' WHERE id=? AND state='RUNNING'"); bind_text(db_, work.get(), 1, id); check(sqlite3_step(work.get()), db_, "mark Work succeeded");
        check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit success transaction");
    } catch (...) { rollback(db_); throw; }
}

void WorkStore::finish_cancelled(const std::string& id, int attempt_number, const std::string& technical_failure, std::optional<int> exit_code, std::optional<int> http_status, std::int64_t ended_at_ms) {
    std::lock_guard lock(mutex_); check(sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr), db_, "begin cancel transaction");
    try {
        Statement attempt(db_, "UPDATE attempts SET state='CANCELLED',ended_at_ms=?,technical_failure=?,exit_code=?,http_status=? WHERE work_id=? AND attempt_number=? AND state='RUNNING'");
        check(sqlite3_bind_int64(attempt.get(), 1, ended_at_ms), db_, "bind cancellation time"); bind_text(db_, attempt.get(), 2, technical_failure); bind_optional_int(db_, attempt.get(), 3, exit_code); bind_optional_int(db_, attempt.get(), 4, http_status);
        bind_text(db_, attempt.get(), 5, id); check(sqlite3_bind_int(attempt.get(), 6, attempt_number), db_, "bind attempt number"); check(sqlite3_step(attempt.get()), db_, "cancel attempt");
        Statement work(db_, "UPDATE work SET state='CANCELLED',technical_failure=? WHERE id=? AND state='RUNNING'"); bind_text(db_, work.get(), 1, technical_failure); bind_text(db_, work.get(), 2, id); check(sqlite3_step(work.get()), db_, "cancel Work");
        check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit cancel transaction");
    } catch (...) { rollback(db_); throw; }
}

void WorkStore::finish_definite_failure(const std::string& id, int attempt_number, const std::string& attempt_state, const std::string& technical_failure, std::optional<int> exit_code, std::optional<int> http_status, std::int64_t ended_at_ms) {
    if (attempt_state != "FAILED" && attempt_state != "TIMED_OUT") throw std::invalid_argument("definite attempt failure state must be FAILED or TIMED_OUT");
    std::lock_guard lock(mutex_); check(sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr), db_, "begin failure transaction");
    try {
        Statement read(db_, (std::string("SELECT ") + kWorkColumns + " FROM work WHERE id=? AND state='RUNNING'").c_str()); bind_text(db_, read.get(), 1, id);
        const auto rc = sqlite3_step(read.get()); if (rc == SQLITE_DONE) { rollback(db_); return; } check(rc, db_, "read running Work"); const auto work = read_work_row(read.get());
        Statement attempt(db_, "UPDATE attempts SET state=?,ended_at_ms=?,technical_failure=?,exit_code=?,http_status=? WHERE work_id=? AND attempt_number=? AND state='RUNNING'");
        bind_text(db_, attempt.get(), 1, attempt_state); check(sqlite3_bind_int64(attempt.get(), 2, ended_at_ms), db_, "bind attempt end"); bind_text(db_, attempt.get(), 3, technical_failure);
        bind_optional_int(db_, attempt.get(), 4, exit_code); bind_optional_int(db_, attempt.get(), 5, http_status); bind_text(db_, attempt.get(), 6, id); check(sqlite3_bind_int(attempt.get(), 7, attempt_number), db_, "bind attempt number"); check(sqlite3_step(attempt.get()), db_, "finish failed attempt");
        if (work.cancel_requested) {
            Statement cancel(db_, "UPDATE work SET state='CANCELLED',technical_failure='CANCELLED_DURING_DEFINITE_FAILURE' WHERE id=? AND state='RUNNING'"); bind_text(db_, cancel.get(), 1, id); check(sqlite3_step(cancel.get()), db_, "cancel Work during failure finalization");
        } else if (retries_definite_failures(work) && attempt_budget_remaining(work, ended_at_ms)) {
            Statement queue(db_, "UPDATE work SET state='QUEUED',next_attempt_at_ms=?,technical_failure=? WHERE id=? AND state='RUNNING'"); check(sqlite3_bind_int64(queue.get(), 1, ended_at_ms + work.retry_delay_ms), db_, "bind retry time"); bind_text(db_, queue.get(), 2, technical_failure); bind_text(db_, queue.get(), 3, id); check(sqlite3_step(queue.get()), db_, "queue Work retry");
        } else {
            Statement fail(db_, "UPDATE work SET state='FAILED',technical_failure=? WHERE id=? AND state='RUNNING'"); bind_text(db_, fail.get(), 1, technical_failure); bind_text(db_, fail.get(), 2, id); check(sqlite3_step(fail.get()), db_, "fail Work after attempt");
        }
        check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit failure transaction");
    } catch (...) { rollback(db_); throw; }
}

void WorkStore::finish_unknown_completion(const std::string& id, int attempt_number, const std::string& technical_failure, std::optional<int> http_status, std::int64_t ended_at_ms) {
    std::lock_guard lock(mutex_); check(sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr), db_, "begin unknown-completion transaction");
    try {
        Statement read(db_, (std::string("SELECT ") + kWorkColumns + " FROM work WHERE id=? AND state='RUNNING'").c_str()); bind_text(db_, read.get(), 1, id);
        const auto rc = sqlite3_step(read.get()); if (rc == SQLITE_DONE) { rollback(db_); return; } check(rc, db_, "read running Work"); const auto work = read_work_row(read.get());
        Statement attempt(db_, "UPDATE attempts SET state='UNKNOWN_COMPLETION',ended_at_ms=?,technical_failure=?,http_status=? WHERE work_id=? AND attempt_number=? AND state='RUNNING'");
        check(sqlite3_bind_int64(attempt.get(), 1, ended_at_ms), db_, "bind unknown end"); bind_text(db_, attempt.get(), 2, technical_failure); bind_optional_int(db_, attempt.get(), 3, http_status); bind_text(db_, attempt.get(), 4, id); check(sqlite3_bind_int(attempt.get(), 5, attempt_number), db_, "bind attempt number"); check(sqlite3_step(attempt.get()), db_, "mark attempt completion unknown");
        if (!work.cancel_requested && retries_unknown_completion(work) && attempt_budget_remaining(work, ended_at_ms)) {
            Statement queue(db_, "UPDATE work SET state='QUEUED',next_attempt_at_ms=?,technical_failure=? WHERE id=? AND state='RUNNING'"); check(sqlite3_bind_int64(queue.get(), 1, ended_at_ms + work.retry_delay_ms), db_, "bind unknown retry time"); bind_text(db_, queue.get(), 2, technical_failure); bind_text(db_, queue.get(), 3, id); check(sqlite3_step(queue.get()), db_, "queue explicitly permitted unknown-completion retry");
        } else {
            Statement unknown(db_, "UPDATE work SET state='UNKNOWN_COMPLETION',technical_failure=? WHERE id=? AND state='RUNNING'"); bind_text(db_, unknown.get(), 1, technical_failure); bind_text(db_, unknown.get(), 2, id); check(sqlite3_step(unknown.get()), db_, "retain unknown completion honestly");
        }
        check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit unknown-completion transaction");
    } catch (...) { rollback(db_); throw; }
}

void WorkStore::recover_interrupted(std::int64_t now_ms) {
    std::lock_guard lock(mutex_); check(sqlite3_exec(db_, "BEGIN IMMEDIATE", nullptr, nullptr, nullptr), db_, "begin restart recovery");
    try {
        Statement rows(db_, (std::string("SELECT ") + kWorkColumns + " FROM work WHERE state='RUNNING'").c_str()); std::vector<WorkRecord> running;
        while (true) { const auto rc = sqlite3_step(rows.get()); if (rc == SQLITE_DONE) break; check(rc, db_, "read running Work for recovery"); running.push_back(read_work_row(rows.get())); }
        for (const auto& work : running) {
            const std::string failure = "UNKNOWN_COMPLETION: Kernel lost certainty about an active physical attempt";
            Statement interrupt(db_, "UPDATE attempts SET state='UNKNOWN_COMPLETION',ended_at_ms=?,technical_failure=? WHERE work_id=? AND attempt_number=? AND state='RUNNING'");
            check(sqlite3_bind_int64(interrupt.get(), 1, now_ms), db_, "bind interruption time"); bind_text(db_, interrupt.get(), 2, failure); bind_text(db_, interrupt.get(), 3, work.id); check(sqlite3_bind_int(interrupt.get(), 4, work.attempt_count), db_, "bind interrupted attempt number"); check(sqlite3_step(interrupt.get()), db_, "mark attempt completion unknown");
            if (!work.cancel_requested && retries_unknown_completion(work) && attempt_budget_remaining(work, now_ms)) {
                Statement queue(db_, "UPDATE work SET state='QUEUED',next_attempt_at_ms=?,technical_failure=? WHERE id=? AND state='RUNNING'"); check(sqlite3_bind_int64(queue.get(), 1, now_ms + work.retry_delay_ms), db_, "bind restart retry time"); bind_text(db_, queue.get(), 2, failure); bind_text(db_, queue.get(), 3, work.id); check(sqlite3_step(queue.get()), db_, "queue explicitly safe unknown-completion retry");
            } else {
                Statement unknown(db_, "UPDATE work SET state='UNKNOWN_COMPLETION',technical_failure=? WHERE id=? AND state='RUNNING'"); bind_text(db_, unknown.get(), 1, failure); bind_text(db_, unknown.get(), 2, work.id); check(sqlite3_step(unknown.get()), db_, "retain unknown completion honestly");
            }
        }
        check(sqlite3_exec(db_, "COMMIT", nullptr, nullptr, nullptr), db_, "commit restart recovery");
    } catch (...) { rollback(db_); throw; }
}

std::string WorkStore::cancel(const std::string& id) {
    std::lock_guard lock(mutex_);
    Statement find(db_, "SELECT state FROM work WHERE id=?"); bind_text(db_, find.get(), 1, id); const auto rc = sqlite3_step(find.get()); if (rc == SQLITE_DONE) return {}; check(rc, db_, "read Work for cancellation"); const auto state = column_text(find.get(), 0);
    if (state == "QUEUED") {
        Statement cancel_statement(db_, "UPDATE work SET state='CANCELLED',cancel_requested=1,technical_failure='CANCELLED_BEFORE_DISPATCH' WHERE id=? AND state='QUEUED'"); bind_text(db_, cancel_statement.get(), 1, id); check(sqlite3_step(cancel_statement.get()), db_, "cancel queued Work"); return "CANCELLED";
    }
    if (state == "RUNNING") {
        Statement request_statement(db_, "UPDATE work SET cancel_requested=1 WHERE id=? AND state='RUNNING'"); bind_text(db_, request_statement.get(), 1, id); check(sqlite3_step(request_statement.get()), db_, "request running Work cancellation"); return "RUNNING";
    }
    return state;
}

bool WorkStore::cancel_requested(const std::string& id) {
    std::lock_guard lock(mutex_); Statement statement(db_, "SELECT cancel_requested FROM work WHERE id=?"); bind_text(db_, statement.get(), 1, id); const auto rc = sqlite3_step(statement.get()); if (rc == SQLITE_DONE) return false; check(rc, db_, "read cancellation request"); return sqlite3_column_int(statement.get(), 0) != 0;
}

bool WorkStore::release(const std::string& id) {
    std::lock_guard lock(mutex_);
    Statement statement(db_, R"SQL(
UPDATE work SET released=1
WHERE id=? AND state IN ('SUCCEEDED','FAILED','CANCELLED','UNKNOWN_COMPLETION')
)SQL");
    bind_text(db_, statement.get(), 1, id); check(sqlite3_step(statement.get()), db_, "release terminal Work payload ownership");
    return sqlite3_changes(db_) == 1;
}

}  // namespace madre::kernel
