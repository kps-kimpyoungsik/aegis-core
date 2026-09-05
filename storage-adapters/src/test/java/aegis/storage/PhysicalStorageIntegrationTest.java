package aegis.storage;

import static aegis.storage.contracts.StorageAdapterContracts.*;

import aegis.storage.postgres.JdbcRecordStoreAdapter;
import aegis.storage.redis.RespRedisRuntimeAdapter;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;
import javax.sql.DataSource;

public final class PhysicalStorageIntegrationTest {
    private static int assertions;

    private PhysicalStorageIntegrationTest() {}

    public static void main(String[] args) throws Exception {
        String pgUrl = env("AEGIS_TEST_POSTGRES_URL");
        String pgUser = env("AEGIS_TEST_POSTGRES_USER");
        String pgPassword = env("AEGIS_TEST_POSTGRES_PASSWORD");
        String redisHost = env("AEGIS_TEST_REDIS_HOST");
        int redisPort = Integer.parseInt(env("AEGIS_TEST_REDIS_PORT"));

        Class.forName("org.postgresql.Driver");
        DataSource dataSource = new DriverManagerDataSource(pgUrl, pgUser, pgPassword);
        initializePostgres(dataSource);

        JdbcRecordStoreAdapter records = new JdbcRecordStoreAdapter(dataSource);
        RecordKey key = new RecordKey("runtime.execution.task", "physical-task-1");
        VersionedRecord v1 = new VersionedRecord(key, 1, "{\"state\":\"RUNNING\"}", "prov://physical/v1");
        check(records.insert(v1, "idem-insert-1").decision() == WriteDecision.COMMITTED, "postgres insert committed");
        check(records.insert(v1, "idem-insert-1").decision() == WriteDecision.DUPLICATE, "postgres duplicate insert blocked");
        check(records.find(key).orElseThrow().version() == 1, "postgres read version 1");

        VersionedRecord v2 = new VersionedRecord(key, 2, "{\"state\":\"VALIDATING\"}", "prov://physical/v2");
        check(records.update(v2, 1, "idem-update-2").decision() == WriteDecision.COMMITTED, "postgres optimistic update committed");
        VersionedRecord stale = new VersionedRecord(key, 3, "{\"state\":\"FAILED\"}", "prov://physical/stale");
        check(records.update(stale, 1, "idem-update-stale").decision() == WriteDecision.VERSION_CONFLICT, "postgres stale update blocked");
        check(records.find(key).orElseThrow().version() == 2, "postgres canonical version preserved");

        RespRedisRuntimeAdapter redis = new RespRedisRuntimeAdapter(redisHost, redisPort, Duration.ofSeconds(2));
        check(redis.probe().reachable(), "redis runtime reachable");
        ProjectionEntry projection = new ProjectionEntry("task:physical-task-1", 2, "{\"state\":\"VALIDATING\"}");
        redis.put(projection, Duration.ofSeconds(3));
        Optional<ProjectionEntry> loaded = redis.get(projection.key());
        check(loaded.isPresent() && loaded.orElseThrow().sourceVersion() == 2, "redis projection roundtrip");
        redis.delete(projection.key());
        check(redis.get(projection.key()).isEmpty(), "redis projection rebuildable delete");

        LeaseGrant first = redis.acquire("task:physical-task-1", "worker-a", Duration.ofSeconds(3)).orElseThrow();
        check(first.fencingToken() > 0, "redis fencing token issued");
        check(redis.acquire("task:physical-task-1", "worker-b", Duration.ofSeconds(3)).isEmpty(), "redis competing lease blocked");
        check(redis.release(first), "redis lease released by holder token");
        LeaseGrant second = redis.acquire("task:physical-task-1", "worker-b", Duration.ofSeconds(3)).orElseThrow();
        check(second.fencingToken() > first.fencingToken(), "redis fencing token monotonic");
        check(redis.release(second), "redis second lease released");

        System.out.println("PASS " + assertions + "/" + assertions);
    }

    private static void initializePostgres(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("drop table if exists aegis_record");
            statement.executeUpdate("create table aegis_record (" +
                    "dataset_id varchar(200) not null," +
                    "record_id varchar(200) not null," +
                    "record_version bigint not null check (record_version >= 0)," +
                    "payload text not null," +
                    "provenance_ref text not null," +
                    "idempotency_key varchar(300) not null unique," +
                    "primary key(dataset_id, record_id))");
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
        @Override public Logger getParentLogger() { return Logger.getLogger("aegis.storage.postgres"); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) return iface.cast(this);
            throw new SQLException("not a wrapper for " + iface.getName());
        }
        @Override public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
    }
}
