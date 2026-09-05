package io.aegis.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.aegis.adapters.postgres.DriverManagerPostgresConnectionProvider;
import io.aegis.server.security.PublicModeSecurity;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

public final class AegisServer {
    private static final String VERSION = "0.3.0-rc3";
    private AegisServer() {}

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("AEGIS_PORT", "8080"));
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/liveness", exchange -> respond(exchange, 200, "{\"status\":\"UP\"}"));
        server.createContext("/readiness", exchange -> {
            boolean postgresReady = postgresReadyIfRequired();
            boolean securityReady = PublicModeSecurity.configurationReady();
            boolean ready = postgresReady && securityReady;
            respond(exchange, ready ? 200 : 503,
                    "{\"status\":\"" + (ready ? "READY" : "NOT_READY") + "\",\"security\":\"" + (securityReady ? "READY" : "NOT_READY") + "\"}");
        });
        server.createContext("/api/v1/system/health", exchange -> {
            if (!authorizePublicApi(exchange, "system:read")) return;
            boolean postgresRequired = Boolean.parseBoolean(System.getenv().getOrDefault("AEGIS_REQUIRE_POSTGRES", "false"));
            boolean postgresReady = !postgresRequired || postgresReadyIfRequired();
            String postgres = postgresRequired ? (postgresReady ? "UP" : "DOWN") : "OPTIONAL";
            respond(exchange, postgresReady ? 200 : 503,
                    "{\"status\":\"" + (postgresReady ? "UP" : "DEGRADED") + "\",\"version\":\"" + VERSION + "\",\"postgres\":\"" + postgres + "\"}");
        });
        server.createContext("/api/v1/whoami", exchange -> {
            if (!PublicModeSecurity.publicMode()) {
                respond(exchange, 200, "{\"mode\":\"internal\",\"tenant\":\"UNSCOPED_INTERNAL\"}");
                return;
            }
            if (!PublicModeSecurity.configurationReady()) {
                respond(exchange, 503, "{\"error\":\"PUBLIC_SECURITY_CONFIGURATION_INVALID\"}");
                return;
            }
            var auth = PublicModeSecurity.authenticator().authenticate(exchange, "system:read");
            if (!auth.allowed()) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                respond(exchange, 401, "{\"error\":\"" + auth.reasonCode() + "\"}");
                return;
            }
            respond(exchange, 200, "{\"mode\":\"public\",\"tenant\":\"" + jsonEscape(auth.tenantId()) + "\",\"scope\":\"" + jsonEscape(auth.scope()) + "\"}");
        });
        server.setExecutor(null);
        server.start();
        System.out.println("AEGIS server " + VERSION + " listening on " + port);
    }

    private static boolean authorizePublicApi(HttpExchange exchange, String scope) throws IOException {
        if (!PublicModeSecurity.publicMode()) return true;
        if (!PublicModeSecurity.configurationReady()) {
            respond(exchange, 503, "{\"error\":\"PUBLIC_SECURITY_CONFIGURATION_INVALID\"}");
            return false;
        }
        var auth = PublicModeSecurity.authenticator().authenticate(exchange, scope);
        if (auth.allowed()) return true;
        exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
        respond(exchange, 401, "{\"error\":\"" + auth.reasonCode() + "\"}");
        return false;
    }

    private static boolean postgresReadyIfRequired() {
        if (!Boolean.parseBoolean(System.getenv().getOrDefault("AEGIS_REQUIRE_POSTGRES", "false"))) return true;
        String url = System.getenv().getOrDefault("AEGIS_POSTGRES_URL", "jdbc:postgresql://localhost:5432/aegis");
        String user = System.getenv().getOrDefault("AEGIS_POSTGRES_USER", "aegis");
        String password = System.getenv().getOrDefault("AEGIS_POSTGRES_PASSWORD", "");
        var provider = new DriverManagerPostgresConnectionProvider(url, user, password);
        try (var connection = provider.open()) {
            return connection.isValid(2);
        } catch (SQLException | RuntimeException unavailable) {
            return false;
        }
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        exchange.sendResponseHeaders(code, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
}
