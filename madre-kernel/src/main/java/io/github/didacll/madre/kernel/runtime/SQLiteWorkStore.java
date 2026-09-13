package io.github.didacll.madre.kernel.runtime;

import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.sdk.execution.CancellationKey;
import io.github.didacll.madre.sdk.execution.PhysicalLocation;
import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkState;
import io.github.didacll.madre.sdk.execution.WorkStatus;
import io.github.didacll.madre.sdk.identity.ModuleId;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite persistence containing only physical-work runtime state and opaque payload bytes. */
public final class SQLiteWorkStore implements AutoCloseable {
    private final Connection connection;

    public SQLiteWorkStore(Path file) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS physical_work (
                          id TEXT PRIMARY KEY, originating_module TEXT NOT NULL, contract_id TEXT NOT NULL,
                          command BLOB, sensitivity TEXT NOT NULL, risk TEXT, priority INTEGER NOT NULL,
                          eligible_at INTEGER NOT NULL, timeout_ms INTEGER NOT NULL, maximum_attempts INTEGER NOT NULL,
                          retry_delay_ms INTEGER NOT NULL, cancellation_key TEXT, preferred_location TEXT,
                          maximum_latency_ms INTEGER, state TEXT NOT NULL, attempts INTEGER NOT NULL,
                          failure_category TEXT, result BLOB, completed_at INTEGER
                        )""");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS physical_attempt (
                          work_id TEXT NOT NULL, attempt INTEGER NOT NULL, capability_id TEXT NOT NULL,
                          started_at INTEGER NOT NULL, finished_at INTEGER, failure_category TEXT,
                          PRIMARY KEY(work_id, attempt), FOREIGN KEY(work_id) REFERENCES physical_work(id) ON DELETE CASCADE
                        )""");
                statement.execute("CREATE INDEX IF NOT EXISTS physical_work_schedule ON physical_work(state, eligible_at, priority DESC)");
            }
            recoverInterrupted();
        } catch (SQLException exception) {
            throw new IllegalStateException("cannot open physical-work store", exception);
        }
    }

    public synchronized void insert(StoredWork work) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO physical_work VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""")) {
            bind(statement, work); statement.executeUpdate();
        } catch (SQLException exception) { throw failure("insert work", exception); }
    }

    public synchronized Optional<StoredWork> find(WorkId id) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM physical_work WHERE id=?")) {
            statement.setString(1, id.value());
            try (ResultSet result = statement.executeQuery()) { return result.next() ? Optional.of(read(result)) : Optional.empty(); }
        } catch (SQLException exception) { throw failure("find work", exception); }
    }

    synchronized List<StoredWork> eligible(Instant now, int limit) {
        List<StoredWork> work = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM physical_work WHERE state='QUEUED' AND eligible_at<=?
                ORDER BY priority DESC, eligible_at ASC, id ASC LIMIT ?""")) {
            statement.setLong(1, now.toEpochMilli()); statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) { while (result.next()) work.add(read(result)); }
            return List.copyOf(work);
        } catch (SQLException exception) { throw failure("list eligible work", exception); }
    }

    synchronized boolean beginAttempt(WorkId id, int expectedAttempts, CapabilityId capability, Instant now) {
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE physical_work SET state='RUNNING', attempts=attempts+1, failure_category=NULL
                    WHERE id=? AND state='QUEUED' AND attempts=?""")) {
                update.setString(1, id.value()); update.setInt(2, expectedAttempts);
                if (update.executeUpdate() != 1) { connection.rollback(); return false; }
            }
            try (PreparedStatement attempt = connection.prepareStatement("INSERT INTO physical_attempt VALUES(?,?,?,?,NULL,NULL)")) {
                attempt.setString(1, id.value()); attempt.setInt(2, expectedAttempts + 1);
                attempt.setString(3, capability.value()); attempt.setLong(4, now.toEpochMilli()); attempt.executeUpdate();
            }
            connection.commit(); return true;
        } catch (SQLException exception) { rollback(); throw failure("begin attempt", exception); }
        finally { autoCommit(); }
    }

    synchronized void succeed(WorkId id, int attempt, byte[] result, Instant now) {
        transaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE physical_work SET state='SUCCEEDED', command=NULL, result=?, completed_at=? WHERE id=? AND state='RUNNING'""")) {
                statement.setBytes(1, result); statement.setLong(2, now.toEpochMilli()); statement.setString(3, id.value()); statement.executeUpdate();
            }
            finishAttempt(id, attempt, now, null);
        });
    }

    synchronized void failAttempt(StoredWork work, PhysicalFailureCategory category, Instant now) {
        int attempt = work.attempts() + 1;
        boolean retry = attempt < work.maximumAttempts();
        transaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE physical_work SET state=?, eligible_at=?, failure_category=?, completed_at=? WHERE id=? AND state='RUNNING'""")) {
                statement.setString(1, retry ? "QUEUED" : "FAILED");
                statement.setLong(2, now.plus(work.retryDelay()).toEpochMilli()); statement.setString(3, category.name());
                if (retry) statement.setNull(4, java.sql.Types.BIGINT); else statement.setLong(4, now.toEpochMilli());
                statement.setString(5, work.id().value()); statement.executeUpdate();
            }
            finishAttempt(work.id(), attempt, now, category.name());
        });
    }

    public synchronized boolean cancel(WorkId id) {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE physical_work SET state='CANCELLED', command=NULL, completed_at=?
                WHERE id=? AND state IN ('QUEUED','RUNNING')""")) {
            statement.setLong(1, Instant.now().toEpochMilli()); statement.setString(2, id.value());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) { throw failure("cancel work", exception); }
    }

    public synchronized int cancel(CancellationKey key) {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE physical_work SET state='CANCELLED', command=NULL, completed_at=?
                WHERE cancellation_key=? AND state IN ('QUEUED','RUNNING')""")) {
            statement.setLong(1, Instant.now().toEpochMilli()); statement.setString(2, key.value()); return statement.executeUpdate();
        } catch (SQLException exception) { throw failure("cancel work group", exception); }
    }

    public synchronized boolean acknowledge(WorkId id) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM physical_work WHERE id=? AND state='SUCCEEDED'")) {
            statement.setString(1, id.value()); return statement.executeUpdate() == 1;
        } catch (SQLException exception) { throw failure("acknowledge work", exception); }
    }

    public synchronized int cleanup(Instant completedBefore) {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM physical_work WHERE completed_at IS NOT NULL AND completed_at<?
                AND state IN ('SUCCEEDED','FAILED','CANCELLED')""")) {
            statement.setLong(1, completedBefore.toEpochMilli()); return statement.executeUpdate();
        } catch (SQLException exception) { throw failure("clean retained work", exception); }
    }

    public Optional<WorkStatus> status(WorkId id) {
        return find(id).map(work -> new WorkStatus(work.id(), work.state(), work.attempts(), work.eligibleAt(), work.failureCategory(), work.completedAt()));
    }

    private void recoverInterrupted() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE physical_attempt SET finished_at=CAST(unixepoch('subsec') * 1000 AS INTEGER),
                    failure_category='INTERRUPTED' WHERE finished_at IS NULL AND EXISTS (
                      SELECT 1 FROM physical_work WHERE physical_work.id=physical_attempt.work_id
                      AND physical_work.state='RUNNING' AND physical_work.attempts=physical_attempt.attempt)
                    """);
            statement.executeUpdate("""
                    UPDATE physical_work SET state='QUEUED', eligible_at=CAST(unixepoch('subsec') * 1000 AS INTEGER),
                    failure_category='INTERRUPTED' WHERE state='RUNNING' AND attempts < maximum_attempts""");
            statement.executeUpdate("""
                    UPDATE physical_work SET state='FAILED', command=NULL, failure_category='INTERRUPTED',
                    completed_at=CAST(unixepoch('subsec') * 1000 AS INTEGER)
                    WHERE state='RUNNING' AND attempts >= maximum_attempts""");
        }
    }

    private void finishAttempt(WorkId id, int attempt, Instant now, String category) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE physical_attempt SET finished_at=?, failure_category=? WHERE work_id=? AND attempt=?""")) {
            statement.setLong(1, now.toEpochMilli());
            if (category == null) statement.setNull(2, java.sql.Types.VARCHAR); else statement.setString(2, category);
            statement.setString(3, id.value()); statement.setInt(4, attempt); statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, StoredWork work) throws SQLException {
        statement.setString(1, work.id().value()); statement.setString(2, work.module().value()); statement.setString(3, work.contractId());
        statement.setBytes(4, work.command()); statement.setString(5, work.sensitivity().name());
        nullable(statement, 6, work.risk().map(Enum::name)); statement.setInt(7, work.priority()); statement.setLong(8, work.eligibleAt().toEpochMilli());
        statement.setLong(9, work.timeout().toMillis()); statement.setInt(10, work.maximumAttempts()); statement.setLong(11, work.retryDelay().toMillis());
        nullable(statement, 12, work.cancellationKey().map(CancellationKey::value)); nullable(statement, 13, work.location().map(Enum::name));
        if (work.maximumLatency().isPresent()) statement.setLong(14, work.maximumLatency().orElseThrow().toMillis()); else statement.setNull(14, java.sql.Types.BIGINT);
        statement.setString(15, work.state().name()); statement.setInt(16, work.attempts()); nullable(statement, 17, work.failureCategory().map(Enum::name));
        if (work.result().isPresent()) statement.setBytes(18, work.result().orElseThrow()); else statement.setNull(18, java.sql.Types.BLOB);
        if (work.completedAt().isPresent()) statement.setLong(19, work.completedAt().orElseThrow().toEpochMilli()); else statement.setNull(19, java.sql.Types.BIGINT);
    }

    private static StoredWork read(ResultSet result) throws SQLException {
        byte[] command = result.getBytes("command"); byte[] output = result.getBytes("result");
        return new StoredWork(new WorkId(result.getString("id")), new ModuleId(result.getString("originating_module")), result.getString("contract_id"), command,
                Sensitivity.valueOf(result.getString("sensitivity")), optionalEnum(Risk.class, result.getString("risk")), result.getInt("priority"),
                Instant.ofEpochMilli(result.getLong("eligible_at")), Duration.ofMillis(result.getLong("timeout_ms")), result.getInt("maximum_attempts"),
                Duration.ofMillis(result.getLong("retry_delay_ms")), optional(result.getString("cancellation_key")).map(CancellationKey::new),
                optionalEnum(PhysicalLocation.class, result.getString("preferred_location")), optionalLong(result, "maximum_latency_ms").map(Duration::ofMillis),
                WorkState.valueOf(result.getString("state")), result.getInt("attempts"), optionalEnum(PhysicalFailureCategory.class, result.getString("failure_category")),
                Optional.ofNullable(output), optionalLong(result, "completed_at").map(Instant::ofEpochMilli));
    }

    private static Optional<Long> optionalLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column); return result.wasNull() ? Optional.empty() : Optional.of(value);
    }
    private static Optional<String> optional(String value) { return Optional.ofNullable(value); }
    private static <E extends Enum<E>> Optional<E> optionalEnum(Class<E> type, String value) { return value == null ? Optional.empty() : Optional.of(Enum.valueOf(type, value)); }
    private static void nullable(PreparedStatement statement, int index, Optional<String> value) throws SQLException {
        if (value.isPresent()) statement.setString(index, value.orElseThrow()); else statement.setNull(index, java.sql.Types.VARCHAR);
    }
    private void transaction(SqlAction action) {
        try { connection.setAutoCommit(false); action.run(); connection.commit(); }
        catch (SQLException exception) { rollback(); throw failure("update work", exception); }
        finally { autoCommit(); }
    }
    private void rollback() { try { connection.rollback(); } catch (SQLException ignored) { /* original failure wins */ } }
    private void autoCommit() { try { connection.setAutoCommit(true); } catch (SQLException exception) { throw failure("restore transaction mode", exception); } }
    private static IllegalStateException failure(String action, SQLException exception) { return new IllegalStateException("cannot " + action, exception); }
    @Override public synchronized void close() { try { connection.close(); } catch (SQLException exception) { throw failure("close work store", exception); } }
    @FunctionalInterface private interface SqlAction { void run() throws SQLException; }
}
