package aegis.integration;

import static aegis.storage.contracts.StorageAdapterContracts.*;

import aegis.storage.postgres.JdbcRecordStoreAdapter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.sql.DataSource;

public final class ProductLivePostgresE2EGateTest {
    private static int assertions;

    private ProductLivePostgresE2EGateTest() {}

    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");
        DataSource dataSource = new DriverManagerDataSource(env("AEGIS_TEST_POSTGRES_URL"), env("AEGIS_TEST_POSTGRES_USER"), env("AEGIS_TEST_POSTGRES_PASSWORD"));
        initializePostgres(dataSource);

        Process process = new ProcessBuilder("node", "product/tools/product-live-postgres-fixture.mjs")
                .redirectErrorStream(true)
                .start();
        String snapshot;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            snapshot = reader.lines().collect(Collectors.joining("\n"));
        }
        int exit = process.waitFor();
        check(exit == 0, "product runtime fixture exits successfully");
        check(snapshot.contains("\"product-live-postgres-1\""), "product task identity present");
        check(snapshot.contains("\"status\":\"COMPLETED\""), "product runtime completed state present");

        JdbcRecordStoreAdapter records = new JdbcRecordStoreAdapter(dataSource);
        RecordKey key = new RecordKey("runtime.execution.snapshot", "product-live-postgres-1");
        VersionedRecord record = new VersionedRecord(key, 1, snapshot, "prov://r1.0/product-live-postgres-e2e");
        check(records.insert(record, "r1.0-product-live-postgres-1").decision() == WriteDecision.COMMITTED, "canonical postgres adapter committed product snapshot");
        VersionedRecord loaded = records.find(key).orElseThrow();
        check(loaded.version() == 1, "canonical postgres adapter returned expected version");
        check(loaded.payloadJson().equals(snapshot), "live postgres roundtrip preserved product runtime snapshot");

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
        @Override public Logger getParentLogger() { return Logger.getLogger("aegis.integration.postgres"); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) return iface.cast(this);
            throw new SQLException("not a wrapper for " + iface.getName());
        }
        @Override public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
    }
}
