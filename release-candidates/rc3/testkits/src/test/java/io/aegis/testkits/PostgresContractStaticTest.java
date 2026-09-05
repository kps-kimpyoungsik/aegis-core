package io.aegis.testkits;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PostgresContractStaticTest {
    private PostgresContractStaticTest() {}
    public static void main(String[] args) throws Exception {
        String sql = Files.readString(Path.of("migrations/postgres/V001__canonical_runtime.sql"));
        require(sql, "aegis_work"); require(sql, "aegis_task"); require(sql, "aegis_execution");
        require(sql, "aegis_command_idempotency"); require(sql, "aegis_outbox_event"); require(sql, "aegis_event_inbox");
        require(sql, "PRIMARY KEY (tenant_id, work_id)"); require(sql, "aggregate_version BIGINT");
        System.out.println("PostgresContractStaticTest PASS");
    }
    private static void require(String value, String token) { if (!value.contains(token)) throw new AssertionError("missing migration contract: " + token); }
}
