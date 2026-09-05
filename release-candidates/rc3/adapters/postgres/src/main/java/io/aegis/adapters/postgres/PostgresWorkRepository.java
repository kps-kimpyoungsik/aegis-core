package io.aegis.adapters.postgres;

import io.aegis.core.Work;
import io.aegis.core.WorkRepository;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;

public final class PostgresWorkRepository implements WorkRepository {
    private final PostgresConnectionProvider connections;
    public PostgresWorkRepository(PostgresConnectionProvider connections) { this.connections = Objects.requireNonNull(connections); }

    @Override public Work saveNew(Work work, String idempotencyKey) {
        Objects.requireNonNull(work); Objects.requireNonNull(idempotencyKey);
        try (var c = connections.open()) {
            c.setAutoCommit(false);
            try {
                String requestHash = Integer.toHexString((work.tenantId() + "|" + work.objective()).hashCode());
                try (var idem = c.prepareStatement("INSERT INTO aegis_command_idempotency(tenant_id,idempotency_key,request_hash,status) VALUES (?,?,?,'COMPLETED') ON CONFLICT DO NOTHING")) {
                    idem.setString(1, work.tenantId()); idem.setString(2, idempotencyKey); idem.setString(3, requestHash); idem.executeUpdate();
                }
                try (var ps = c.prepareStatement("INSERT INTO aegis_work(tenant_id,work_id,objective,aggregate_version,created_at,updated_at) VALUES (?,?,?,?,?,?)")) {
                    ps.setString(1, work.tenantId()); ps.setString(2, work.id()); ps.setString(3, work.objective()); ps.setLong(4, work.version());
                    var ts = Timestamp.from(work.createdAt()); ps.setTimestamp(5, ts); ps.setTimestamp(6, ts); ps.executeUpdate();
                }
                try (var outbox = c.prepareStatement("INSERT INTO aegis_outbox_event(tenant_id,event_id,aggregate_type,aggregate_id,event_type,payload,created_at) VALUES (?,?,?,?,?,?,?)")) {
                    outbox.setString(1, work.tenantId()); outbox.setString(2, java.util.UUID.randomUUID().toString()); outbox.setString(3, "WORK"); outbox.setString(4, work.id()); outbox.setString(5, "WORK_CREATED"); outbox.setString(6, "{\"workId\":\"" + work.id() + "\"}"); outbox.setTimestamp(7, Timestamp.from(work.createdAt())); outbox.executeUpdate();
                }
                c.commit(); return work;
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("POSTGRES_WORK_WRITE_FAILED", e); }
    }

    @Override public Optional<Work> findById(String tenantId, String workId) {
        try (var c = connections.open(); var ps = c.prepareStatement("SELECT work_id,tenant_id,objective,aggregate_version,created_at FROM aegis_work WHERE tenant_id=? AND work_id=?")) {
            ps.setString(1, tenantId); ps.setString(2, workId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Work(rs.getString("work_id"), rs.getString("tenant_id"), rs.getString("objective"), rs.getLong("aggregate_version"), rs.getTimestamp("created_at").toInstant()));
            }
        } catch (SQLException e) { throw new IllegalStateException("POSTGRES_WORK_READ_FAILED", e); }
    }
}
