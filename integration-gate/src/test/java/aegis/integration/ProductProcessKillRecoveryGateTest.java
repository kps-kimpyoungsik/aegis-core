package aegis.integration;

import static aegis.storage.contracts.StorageAdapterContracts.*;

import aegis.runtime.kernel.RuntimeExecutionStateKernel;
import aegis.runtime.kernel.RuntimeRecoveryReplayKernel;
import aegis.storage.postgres.JdbcRecordStoreAdapter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.sql.DataSource;

public final class ProductProcessKillRecoveryGateTest {
    private static int assertions;

    private ProductProcessKillRecoveryGateTest() {}

    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");
        DataSource dataSource = new DriverManagerDataSource(
                env("AEGIS_TEST_POSTGRES_URL"),
                env("AEGIS_TEST_POSTGRES_USER"),
                env("AEGIS_TEST_POSTGRES_PASSWORD"));
        initializePostgres(dataSource);

        String executionId = "product-process-kill-1";
        Process child = new ProcessBuilder("node", "product/tools/product-process-kill-fixture.mjs")
                .redirectErrorStream(true)
                .start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            String active = reader.readLine();
            check(("AEGIS_PRODUCT_RUNTIME_ACTIVE " + executionId).equals(active),
                    "product runtime reached active harness state");

            Instant t0 = Instant.parse("2026-09-05T00:00:00Z");
            RuntimeExecutionStateKernel.ExecutionContext context = RuntimeExecutionStateKernel.create(executionId, t0);
            context = RuntimeExecutionStateKernel.transition(context, RuntimeExecutionStateKernel.ExecutionState.READY, t0.plusSeconds(1));
            context = RuntimeExecutionStateKernel.transition(context, RuntimeExecutionStateKernel.ExecutionState.RUNNING, t0.plusSeconds(2));
            context = RuntimeExecutionStateKernel.transition(context, RuntimeExecutionStateKernel.ExecutionState.WAITING, t0.plusSeconds(3));
            RuntimeRecoveryReplayKernel.Checkpoint checkpoint = RuntimeRecoveryReplayKernel.checkpoint(
                    context, "cp-product-process-kill-1", 7, Set.of("effect-before-kill"), t0.plusSeconds(4));

            JdbcRecordStoreAdapter records = new JdbcRecordStoreAdapter(dataSource);
            RecordKey key = new RecordKey("runtime.recovery.checkpoint", checkpoint.checkpointId());
            String payload = String.join("|",
                    checkpoint.executionId(),
                    Long.toString(checkpoint.epoch()),
                    Long.toString(checkpoint.stateVersion()),
                    checkpoint.state().name(),
                    Long.toString(checkpoint.eventSequence()),
                    checkpoint.createdAt().toString());
            VersionedRecord persisted = new VersionedRecord(
                    key, 1, payload, "prov://r1.1/product-process-kill-recovery");
            check(records.insert(persisted, "r1.1-process-kill-checkpoint-1").decision() == WriteDecision.COMMITTED,
                    "canonical postgres adapter persisted checkpoint before kill");

            child.destroyForcibly();
            check(child.waitFor(10, TimeUnit.SECONDS), "product runtime terminated after force kill");
            check(!child.isAlive(), "product runtime is no longer alive");

            VersionedRecord loaded = records.find(key).orElseThrow();
            check(loaded.version() == 1, "checkpoint record version preserved");
            String[] parts = loaded.payloadJson().split("\\|", -1);
            check(parts.length == 6, "persisted checkpoint payload is complete");

            RuntimeRecoveryReplayKernel.Checkpoint recovered = new RuntimeRecoveryReplayKernel.Checkpoint(
                    checkpoint.checkpointId(),
                    parts[0],
                    Long.parseLong(parts[1]),
                    Long.parseLong(parts[2]),
                    RuntimeExecutionStateKernel.ExecutionState.valueOf(parts[3]),
                    Long.parseLong(parts[4]),
                    Set.of("effect-before-kill"),
                    Instant.parse(parts[5]));

            RuntimeRecoveryReplayKernel.RecoveryResult decision = RuntimeRecoveryReplayKernel.resumeDecision(
                    recovered, executionId, recovered.epoch(), recovered.stateVersion());
            check(decision.decision() == RuntimeRecoveryReplayKernel.RecoveryDecision.RESUME,
                    "canonical recovery kernel accepts persisted checkpoint");

            RuntimeExecutionStateKernel.ExecutionContext recoveredContext = new RuntimeExecutionStateKernel.ExecutionContext(
                    recovered.executionId(), recovered.epoch(), recovered.stateVersion(), recovered.state(),
                    t0, t0.plusSeconds(3));
            RuntimeExecutionStateKernel.ExecutionContext resumed = RuntimeExecutionStateKernel.resumeFromCheckpoint(
                    recoveredContext, recovered.epoch(), t0.plusSeconds(5));
            check(resumed.state() == RuntimeExecutionStateKernel.ExecutionState.RUNNING,
                    "canonical execution kernel resumes into RUNNING");
            check(resumed.epoch() == recovered.epoch() + 1,
                    "resume advances execution epoch");
            check(!RuntimeExecutionStateKernel.acceptsResult(resumed, executionId, recovered.epoch()),
                    "stale pre-kill epoch result is rejected");

            RuntimeRecoveryReplayKernel.ReplayEvent duplicateEffect = new RuntimeRecoveryReplayKernel.ReplayEvent(
                    "evt-after-kill", executionId, recovered.epoch(), recovered.eventSequence() + 1, "effect-before-kill");
            check(RuntimeRecoveryReplayKernel.replayDecision(recovered, duplicateEffect, Set.of()).decision()
                            == RuntimeRecoveryReplayKernel.RecoveryDecision.SKIP_DUPLICATE,
                    "completed pre-kill effect is not replayed");
        } finally {
            if (child.isAlive()) child.destroyForcibly();
        }

        System.out.println("PASS " + assertions + "/" + assertions);
    }

    private static void initializePostgres(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("drop table if exists aegis_record");
            statement.executeUpdate("create table aegis_record (dataset_id varchar(200) not null,record_id varchar(200) not null,record_version bigint not null check (record_version >= 0),payload text not null,provenance_ref text not null,idempotency_key varchar(300) not null unique,primary key(dataset_id, record_id))");
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("missing environment variable: " + name);
        return value;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class DriverManagerDataSource implements DataSource {
        private final String url;
        private final String user;
        private final String password;

        private DriverManagerDataSource(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, user, password); }
        @Override public Connection getConnection(String username, String pwd) throws SQLException { return DriverManager.getConnection(url, username, pwd); }
        @Override public PrintWriter getLogWriter() { return DriverManager.getLogWriter(); }
        @Override public void setLogWriter(PrintWriter out) { DriverManager.setLogWriter(out); }
        @Override public void setLoginTimeout(int seconds) { DriverManager.setLoginTimeout(seconds); }
        @Override public int getLoginTimeout() { return DriverManager.getLoginTimeout(); }
        @Override public Logger getParentLogger() { return Logger.getLogger("aegis.integration.processkill"); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) return iface.cast(this);
            throw new SQLException("not a wrapper for " + iface.getName());
        }
        @Override public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
    }
}
