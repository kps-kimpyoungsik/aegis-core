package aegis.storage;

import static aegis.storage.contracts.StorageAdapterContracts.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import aegis.storage.redis.RespRedisRuntimeAdapter;

public final class StorageAdapterContractTest {
    private static int assertions;
    private StorageAdapterContractTest() {}

    public static void main(String[] args) throws Exception {
        RecordKey key = new RecordKey("runtime.execution.task", "task-1");
        VersionedRecord record = new VersionedRecord(key, 3, "{}", "prov://task-1");
        check(record.version() == 3, "version preserved");
        expectFailure(() -> new RecordKey("", "task"), "blank dataset blocked");
        expectFailure(() -> new VersionedRecord(key, -1, "{}", "prov"), "negative version blocked");
        ProjectionEntry projection = new ProjectionEntry("task:1", 3, "{}");
        check(projection.sourceVersion() == 3, "projection source version preserved");
        expectFailure(() -> new ProjectionEntry("task:1", -1, "{}"), "projection requires source version");
        LeaseGrant grant = new LeaseGrant("task:1", "worker-a", 4, Duration.ofSeconds(5));
        check(grant.fencingToken() == 4, "fencing token preserved");
        expectFailure(() -> new LeaseGrant("task:1", "worker-a", 0, Duration.ofSeconds(5)), "zero fencing blocked");
        expectFailure(() -> new LeaseGrant("task:1", "worker-a", 1, Duration.ZERO), "zero ttl blocked");

        var encode = RespRedisRuntimeAdapter.class.getDeclaredMethod("encode", String[].class);
        encode.setAccessible(true);
        byte[] bytes = (byte[]) encode.invoke(null, (Object) new String[] {"SET", "a", "값"});
        String wire = new String(bytes, StandardCharsets.UTF_8);
        check(wire.startsWith("*3\r\n$3\r\nSET\r\n$1\r\na\r\n"), "RESP framing stable");
        check(wire.contains("$3\r\n값\r\n"), "RESP byte length uses UTF-8 bytes");

        expectFailure(() -> new RespRedisRuntimeAdapter("", 6379, Duration.ofSeconds(1)), "blank redis host blocked");
        expectFailure(() -> new RespRedisRuntimeAdapter("localhost", 0, Duration.ofSeconds(1)), "invalid redis port blocked");
        expectFailure(() -> new RespRedisRuntimeAdapter("localhost", 6379, Duration.ZERO), "zero connect timeout blocked");

        System.out.println("PASS " + assertions + "/" + assertions);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Runnable action, String message) {
        assertions++;
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
