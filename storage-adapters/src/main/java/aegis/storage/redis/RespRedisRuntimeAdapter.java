package aegis.storage.redis;

import static aegis.storage.contracts.StorageAdapterContracts.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RespRedisRuntimeAdapter implements ProjectionStorePort, LeaseLockPort {
    private final String host;
    private final int port;
    private final Duration connectTimeout;

    public RespRedisRuntimeAdapter(String host, int port, Duration connectTimeout) {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host must be nonblank");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("invalid port");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        if (connectTimeout.isNegative() || connectTimeout.isZero()) throw new IllegalArgumentException("connectTimeout must be positive");
        this.host = host;
        this.port = port;
        this.connectTimeout = connectTimeout;
    }

    @Override
    public Optional<ProjectionEntry> get(String key) {
        requireText(key, "key");
        String version = bulk(command("GET", projectionVersionKey(key)));
        String payload = bulk(command("GET", projectionPayloadKey(key)));
        if (version == null || payload == null) return Optional.empty();
        return Optional.of(new ProjectionEntry(key, Long.parseLong(version), payload));
    }

    @Override
    public void put(ProjectionEntry entry, Duration ttl) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("ttl must be positive");
        long millis = ttl.toMillis();
        command("SET", projectionVersionKey(entry.key()), Long.toString(entry.sourceVersion()), "PX", Long.toString(millis));
        command("SET", projectionPayloadKey(entry.key()), entry.payloadJson(), "PX", Long.toString(millis));
    }

    @Override
    public void delete(String key) {
        requireText(key, "key");
        command("DEL", projectionVersionKey(key), projectionPayloadKey(key));
    }

    @Override
    public Optional<LeaseGrant> acquire(String resource, String holder, Duration ttl) {
        requireText(resource, "resource");
        requireText(holder, "holder");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("ttl must be positive");
        long token = integer(command("INCR", fencingKey(resource)));
        RespValue set = command("SET", leaseKey(resource), holder + ":" + token, "NX", "PX", Long.toString(ttl.toMillis()));
        if (set instanceof NullBulk) return Optional.empty();
        return Optional.of(new LeaseGrant(resource, holder, token, ttl));
    }

    @Override
    public boolean release(LeaseGrant grant) {
        Objects.requireNonNull(grant, "grant");
        String expected = grant.holder() + ":" + grant.fencingToken();
        String script = "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
        return integer(command("EVAL", script, "1", leaseKey(grant.resource()), expected)) == 1L;
    }

    public RuntimeProbe probe() {
        try {
            RespValue pong = command("PING");
            boolean ok = pong instanceof SimpleString s && "PONG".equals(s.value());
            return new RuntimeProbe(ok, "redis-resp", ok ? "redis:PONG" : "redis:unexpected-probe");
        } catch (RuntimeException ex) {
            return new RuntimeProbe(false, "redis-resp", "redis:unreachable");
        }
    }

    private RespValue command(String... args) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), Math.toIntExact(connectTimeout.toMillis()));
            socket.setSoTimeout(Math.toIntExact(connectTimeout.toMillis()));
            try (BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream()); BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {
                out.write(encode(args));
                out.flush();
                return read(in);
            }
        } catch (IOException ex) {
            throw new RedisAdapterException("redis command failed", ex);
        }
    }

    static byte[] encode(String... args) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeAscii(out, "*" + args.length + "\r\n");
            for (String arg : args) {
                byte[] bytes = Objects.requireNonNull(arg, "RESP arg").getBytes(StandardCharsets.UTF_8);
                writeAscii(out, "$" + bytes.length + "\r\n");
                out.write(bytes);
                writeAscii(out, "\r\n");
            }
            return out.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static RespValue read(BufferedInputStream in) throws IOException {
        int marker = in.read();
        if (marker < 0) throw new IOException("unexpected EOF");
        String line = readLine(in);
        return switch (marker) {
            case '+' -> new SimpleString(line);
            case '-' -> throw new RedisAdapterException("redis error: " + line, null);
            case ':' -> new IntegerValue(Long.parseLong(line));
            case '$' -> readBulk(in, Integer.parseInt(line));
            case '*' -> readArray(in, Integer.parseInt(line));
            default -> throw new IOException("unsupported RESP marker: " + marker);
        };
    }

    private static RespValue readBulk(BufferedInputStream in, int length) throws IOException {
        if (length == -1) return NullBulk.INSTANCE;
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new IOException("short bulk reply");
        if (in.read() != '\r' || in.read() != '\n') throw new IOException("bad bulk terminator");
        return new BulkString(new String(bytes, StandardCharsets.UTF_8));
    }

    private static RespValue readArray(BufferedInputStream in, int count) throws IOException {
        List<RespValue> values = new ArrayList<>(Math.max(count, 0));
        for (int i = 0; i < count; i++) values.add(read(in));
        return new ArrayValue(List.copyOf(values));
    }

    private static String readLine(BufferedInputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int current = in.read();
            if (current < 0) throw new IOException("unexpected EOF");
            if (previous == '\r' && current == '\n') break;
            if (previous >= 0) out.write(previous);
            previous = current;
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String bulk(RespValue value) {
        if (value instanceof NullBulk) return null;
        if (value instanceof BulkString bulk) return bulk.value();
        throw new RedisAdapterException("expected bulk reply", null);
    }

    private static long integer(RespValue value) {
        if (value instanceof IntegerValue integer) return integer.value();
        throw new RedisAdapterException("expected integer reply", null);
    }

    private static String projectionVersionKey(String key) { return "aegis:projection:" + key + ":version"; }
    private static String projectionPayloadKey(String key) { return "aegis:projection:" + key + ":payload"; }
    private static String leaseKey(String resource) { return "aegis:lease:" + resource; }
    private static String fencingKey(String resource) { return "aegis:fence:" + resource; }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be nonblank");
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) throws IOException { out.write(value.getBytes(StandardCharsets.US_ASCII)); }

    sealed interface RespValue permits SimpleString, BulkString, IntegerValue, ArrayValue, NullBulk {}
    record SimpleString(String value) implements RespValue {}
    record BulkString(String value) implements RespValue {}
    record IntegerValue(long value) implements RespValue {}
    record ArrayValue(List<RespValue> values) implements RespValue {}
    enum NullBulk implements RespValue { INSTANCE }

    public static final class RedisAdapterException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public RedisAdapterException(String message, Throwable cause) { super(message, cause); }
    }
}
