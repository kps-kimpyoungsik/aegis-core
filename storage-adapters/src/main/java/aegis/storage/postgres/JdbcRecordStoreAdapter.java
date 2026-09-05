package aegis.storage.postgres;

import static aegis.storage.contracts.StorageAdapterContracts.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcRecordStoreAdapter implements RecordStorePort {
    private final DataSource dataSource;

    public JdbcRecordStoreAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<VersionedRecord> find(RecordKey key) {
        Objects.requireNonNull(key, "key");
        final String sql = "select record_version,payload,provenance_ref from aegis_record where dataset_id=? and record_id=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key.datasetId());
            ps.setString(2, key.recordId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new VersionedRecord(key, rs.getLong(1), rs.getString(2), rs.getString(3)));
            }
        } catch (SQLException ex) {
            throw new StorageAdapterException("postgres find failed", ex);
        }
    }

    @Override
    public WriteResult insert(VersionedRecord record, String idempotencyKey) {
        Objects.requireNonNull(record, "record");
        requireIdempotency(idempotencyKey);
        final String sql = "insert into aegis_record(dataset_id,record_id,record_version,payload,provenance_ref,idempotency_key) values(?,?,?,?,?,?) on conflict do nothing";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, record.key().datasetId());
            ps.setString(2, record.key().recordId());
            ps.setLong(3, record.version());
            ps.setString(4, record.payloadJson());
            ps.setString(5, record.provenanceRef());
            ps.setString(6, idempotencyKey);
            int changed = ps.executeUpdate();
            return changed == 1
                    ? new WriteResult(WriteDecision.COMMITTED, record.version(), "jdbc:insert")
                    : new WriteResult(WriteDecision.DUPLICATE, record.version(), "jdbc:insert-conflict");
        } catch (SQLException ex) {
            throw new StorageAdapterException("postgres insert failed", ex);
        }
    }

    @Override
    public WriteResult update(VersionedRecord candidate, long expectedVersion, String idempotencyKey) {
        Objects.requireNonNull(candidate, "candidate");
        requireIdempotency(idempotencyKey);
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must be >= 0");
        final String sql = "update aegis_record set record_version=?,payload=?,provenance_ref=?,idempotency_key=? where dataset_id=? and record_id=? and record_version=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, candidate.version());
            ps.setString(2, candidate.payloadJson());
            ps.setString(3, candidate.provenanceRef());
            ps.setString(4, idempotencyKey);
            ps.setString(5, candidate.key().datasetId());
            ps.setString(6, candidate.key().recordId());
            ps.setLong(7, expectedVersion);
            int changed = ps.executeUpdate();
            return changed == 1
                    ? new WriteResult(WriteDecision.COMMITTED, candidate.version(), "jdbc:update")
                    : new WriteResult(WriteDecision.VERSION_CONFLICT, expectedVersion, "jdbc:version-conflict");
        } catch (SQLException ex) {
            throw new StorageAdapterException("postgres update failed", ex);
        }
    }

    private static void requireIdempotency(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("idempotencyKey must be nonblank");
    }

    public static final class StorageAdapterException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public StorageAdapterException(String message, Throwable cause) { super(message, cause); }
    }
}
