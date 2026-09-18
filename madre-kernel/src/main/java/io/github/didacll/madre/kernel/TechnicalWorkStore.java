package io.github.didacll.madre.kernel;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite ledger deliberately limited to technical inference lifecycle evidence. */
final class TechnicalWorkStore implements AutoCloseable {
    private final Connection connection;

    TechnicalWorkStore(Path database) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS inference_work (
                          work_id TEXT PRIMARY KEY,
                          inference_type TEXT NOT NULL,
                          status TEXT NOT NULL,
                          placement TEXT NOT NULL,
                          capability_kind TEXT NOT NULL,
                          capability_a INTEGER NOT NULL,
                          capability_b INTEGER NOT NULL,
                          urgency TEXT NOT NULL,
                          eligible_at TEXT NOT NULL,
                          deadline TEXT,
                          timeout_ms INTEGER NOT NULL,
                          maximum_attempts INTEGER NOT NULL,
                          retry_delay_ms INTEGER NOT NULL,
                          maximum_latency_ms INTEGER,
                          exact_engine TEXT,
                          exact_provider TEXT,
                          exact_model TEXT,
                          attempts INTEGER NOT NULL DEFAULT 0,
                          last_engine TEXT,
                          failure_category TEXT,
                          failure_message TEXT,
                          failure_retryable INTEGER,
                          created_at TEXT NOT NULL,
                          updated_at TEXT NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS work_resource (
                          work_id TEXT NOT NULL REFERENCES inference_work(work_id) ON DELETE CASCADE,
                          resource_id TEXT NOT NULL,
                          units INTEGER NOT NULL,
                          PRIMARY KEY(work_id, resource_id)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS inference_attempt (
                          work_id TEXT NOT NULL REFERENCES inference_work(work_id) ON DELETE CASCADE,
                          attempt INTEGER NOT NULL,
                          engine_id TEXT NOT NULL,
                          provider TEXT NOT NULL,
                          model TEXT NOT NULL,
                          started_at TEXT NOT NULL,
                          finished_at TEXT,
                          outcome TEXT,
                          duration_ms INTEGER,
                          PRIMARY KEY(work_id, attempt)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS attempt_resource (
                          work_id TEXT NOT NULL,
                          attempt INTEGER NOT NULL,
                          resource_id TEXT NOT NULL,
                          units INTEGER NOT NULL,
                          PRIMARY KEY(work_id, attempt, resource_id),
                          FOREIGN KEY(work_id, attempt) REFERENCES inference_attempt(work_id, attempt) ON DELETE CASCADE
                        )
                        """);
                statement.execute("""
                        UPDATE inference_work SET status = 'NEEDS_INPUT'
                        WHERE status IN ('QUEUED', 'RUNNING', 'RETRY_WAIT')
                        """);
                statement.execute("""
                        UPDATE inference_work SET status = 'OUTCOME_UNKNOWN',
                          failure_category = 'DELIVERY_FAILURE',
                          failure_message = 'Result delivery was interrupted', failure_retryable = 0
                        WHERE status = 'DELIVERING'
                        """);
            }
        } catch (SQLException exception) {
            throw new KernelException("Cannot open Kernel inference ledger", exception);
        }
    }

    synchronized void insert(InferenceWork<?, ?> work) {
        String sql = """
                INSERT INTO inference_work(work_id,inference_type,status,placement,capability_kind,
                capability_a,capability_b,urgency,eligible_at,deadline,timeout_ms,maximum_attempts,
                retry_delay_ms,maximum_latency_ms,exact_engine,exact_provider,exact_model,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        InferenceRequirements requirements = work.requirements();
        CapabilityColumns capability = CapabilityColumns.from(requirements.capability());
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, work.id().toString());
                statement.setString(2, work.type().id());
                statement.setString(3, WorkStatus.QUEUED.name());
                statement.setString(4, requirements.placement().name());
                statement.setString(5, capability.kind());
                statement.setInt(6, capability.a());
                statement.setInt(7, capability.b());
                statement.setString(8, requirements.urgency().name());
                statement.setString(9, requirements.eligibleAt().toString());
                setOptional(statement, 10, requirements.deadline().map(Instant::toString));
                statement.setLong(11, requirements.timeout().toMillis());
                statement.setInt(12, requirements.retryPolicy().maximumAttempts());
                statement.setLong(13, requirements.retryPolicy().delay().toMillis());
                setOptionalLong(statement, 14, requirements.maximumExpectedLatency().map(value -> value.toMillis()));
                setOptional(statement, 15, requirements.exactEngine().map(EngineId::value));
                setOptional(statement, 16, requirements.exactProvider());
                setOptional(statement, 17, requirements.exactModel());
                statement.setString(18, work.createdAt().toString());
                statement.setString(19, work.createdAt().toString());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO work_resource(work_id,resource_id,units) VALUES(?,?,?)")) {
                for (ResourceClaim claim : requirements.resources()) {
                    statement.setString(1, work.id().toString());
                    statement.setString(2, claim.resource().value());
                    statement.setLong(3, claim.units());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            rollback();
            throw new KernelException("Cannot persist inference work " + work.id(), exception);
        } finally {
            autoCommit();
        }
    }

    synchronized void requireSame(InferenceWork<?, ?> work) {
        String sql = "SELECT * FROM inference_work WHERE work_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, work.id().toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new KernelException("Unknown inference work " + work.id());
                InferenceRequirements r = work.requirements();
                CapabilityColumns c = CapabilityColumns.from(r.capability());
                boolean same = work.type().id().equals(result.getString("inference_type"))
                        && r.placement().name().equals(result.getString("placement"))
                        && c.kind().equals(result.getString("capability_kind"))
                        && c.a() == result.getInt("capability_a") && c.b() == result.getInt("capability_b")
                        && r.urgency().name().equals(result.getString("urgency"))
                        && r.timeout().toMillis() == result.getLong("timeout_ms")
                        && r.retryPolicy().maximumAttempts() == result.getInt("maximum_attempts")
                        && r.retryPolicy().delay().toMillis() == result.getLong("retry_delay_ms")
                        && optionalEquals(r.exactEngine().map(EngineId::value), result.getString("exact_engine"))
                        && optionalEquals(r.exactProvider(), result.getString("exact_provider"))
                        && optionalEquals(r.exactModel(), result.getString("exact_model"));
                if (!same) throw new KernelException("Reattached work does not match persisted technical work " + work.id());
            }
        } catch (SQLException exception) {
            throw new KernelException("Cannot validate inference work " + work.id(), exception);
        }
    }

    synchronized WorkSnapshot snapshot(WorkId id) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM inference_work WHERE work_id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new KernelException("Unknown inference work " + id);
                String engine = result.getString("last_engine");
                String category = result.getString("failure_category");
                Optional<TechnicalFailure> failure = category == null ? Optional.empty() : Optional.of(
                        new TechnicalFailure(TechnicalFailure.Category.valueOf(category),
                                result.getString("failure_message"), result.getInt("failure_retryable") != 0));
                return new WorkSnapshot(id, result.getString("inference_type"),
                        WorkStatus.valueOf(result.getString("status")), result.getInt("attempts"),
                        engine == null ? Optional.empty() : Optional.of(new EngineId(engine)), failure,
                        Instant.parse(result.getString("created_at")), Instant.parse(result.getString("updated_at")));
            }
        } catch (SQLException exception) {
            throw new KernelException("Cannot inspect inference work " + id, exception);
        }
    }

    synchronized List<WorkId> recoverable() {
        List<WorkId> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT work_id FROM inference_work WHERE status IN ('NEEDS_INPUT','OUTCOME_UNKNOWN') ORDER BY created_at");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) ids.add(WorkId.parse(result.getString(1)));
            return List.copyOf(ids);
        } catch (SQLException exception) {
            throw new KernelException("Cannot inspect recoverable inference work", exception);
        }
    }

    synchronized void transition(WorkId id, WorkStatus status, TechnicalFailure failure) {
        String sql = """
                UPDATE inference_work SET status=?,failure_category=?,failure_message=?,failure_retryable=?,updated_at=?
                WHERE work_id=?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            if (failure == null) {
                statement.setNull(2, java.sql.Types.VARCHAR);
                statement.setNull(3, java.sql.Types.VARCHAR);
                statement.setNull(4, java.sql.Types.INTEGER);
            } else {
                statement.setString(2, failure.category().name());
                statement.setString(3, failure.message());
                statement.setInt(4, failure.retryable() ? 1 : 0);
            }
            statement.setString(5, Instant.now().toString());
            statement.setString(6, id.toString());
            if (statement.executeUpdate() != 1) throw new KernelException("Unknown inference work " + id);
        } catch (SQLException exception) {
            throw new KernelException("Cannot update inference work " + id, exception);
        }
    }

    synchronized int startAttempt(WorkId id, InferenceEngine<?, ?> engine, List<ResourceClaim> resources) {
        try {
            connection.setAutoCommit(false);
            int attempt;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE inference_work SET attempts=attempts+1,status='RUNNING',last_engine=?,updated_at=? WHERE work_id=?")) {
                statement.setString(1, engine.id().value());
                statement.setString(2, Instant.now().toString());
                statement.setString(3, id.toString());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT attempts FROM inference_work WHERE work_id=?")) {
                statement.setString(1, id.toString());
                try (ResultSet result = statement.executeQuery()) { result.next(); attempt = result.getInt(1); }
            }
            EngineCharacteristics characteristics = engine.characteristics();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO inference_attempt(work_id,attempt,engine_id,provider,model,started_at)
                    VALUES(?,?,?,?,?,?)
                    """)) {
                statement.setString(1, id.toString()); statement.setInt(2, attempt);
                statement.setString(3, engine.id().value()); statement.setString(4, characteristics.provider());
                statement.setString(5, characteristics.model()); statement.setString(6, Instant.now().toString());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO attempt_resource(work_id,attempt,resource_id,units) VALUES(?,?,?,?)
                    """)) {
                for (ResourceClaim claim : resources) {
                    statement.setString(1, id.toString()); statement.setInt(2, attempt);
                    statement.setString(3, claim.resource().value()); statement.setLong(4, claim.units());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
            return attempt;
        } catch (SQLException exception) {
            rollback();
            throw new KernelException("Cannot start inference attempt " + id, exception);
        } finally { autoCommit(); }
    }

    synchronized void finishAttempt(WorkId id, int attempt, String outcome, long durationMillis) {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE inference_attempt SET finished_at=?,outcome=?,duration_ms=? WHERE work_id=? AND attempt=?
                """)) {
            statement.setString(1, Instant.now().toString()); statement.setString(2, outcome);
            statement.setLong(3, durationMillis); statement.setString(4, id.toString()); statement.setInt(5, attempt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new KernelException("Cannot finish inference attempt " + id, exception);
        }
    }

    private static boolean optionalEquals(Optional<String> expected, String actual) {
        return expected.map(value -> value.equals(actual)).orElse(actual == null);
    }
    private static void setOptional(PreparedStatement statement, int index, Optional<String> value) throws SQLException {
        if (value.isPresent()) statement.setString(index, value.orElseThrow());
        else statement.setNull(index, java.sql.Types.VARCHAR);
    }
    private static void setOptionalLong(PreparedStatement statement, int index, Optional<Long> value) throws SQLException {
        if (value.isPresent()) statement.setLong(index, value.orElseThrow());
        else statement.setNull(index, java.sql.Types.BIGINT);
    }
    private void rollback() { try { connection.rollback(); } catch (SQLException ignored) { /* original failure wins */ } }
    private void autoCommit() { try { connection.setAutoCommit(true); } catch (SQLException exception) { throw new KernelException("Cannot restore ledger transaction mode", exception); } }

    @Override public synchronized void close() {
        try { connection.close(); } catch (SQLException exception) { throw new KernelException("Cannot close Kernel inference ledger", exception); }
    }

    private record CapabilityColumns(String kind, int a, int b) {
        static CapabilityColumns from(TechnicalCapabilityRequirement requirement) {
            return switch (requirement) {
                case TechnicalCapabilityRequirement.None ignored -> new CapabilityColumns("NONE", 0, 0);
                case TechnicalCapabilityRequirement.Text value -> new CapabilityColumns("TEXT", value.demandingReasoning() ? 1 : 0, value.minimumContextTokens());
                case TechnicalCapabilityRequirement.Embedding value -> new CapabilityColumns("EMBEDDING", value.dimensions(), 0);
                case TechnicalCapabilityRequirement.Vision value -> new CapabilityColumns("VISION", value.imageInput() ? 1 : 0, value.videoInput() ? 1 : 0);
            };
        }
    }
}
