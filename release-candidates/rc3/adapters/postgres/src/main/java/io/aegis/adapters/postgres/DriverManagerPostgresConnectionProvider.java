package io.aegis.adapters.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public final class DriverManagerPostgresConnectionProvider implements PostgresConnectionProvider {
    private final String url; private final String user; private final String password;
    public DriverManagerPostgresConnectionProvider(String url, String user, String password) {
        this.url = Objects.requireNonNull(url); this.user = Objects.requireNonNull(user); this.password = Objects.requireNonNull(password);
    }
    @Override public Connection open() throws SQLException { return DriverManager.getConnection(url, user, password); }
}
