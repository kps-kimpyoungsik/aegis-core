package io.aegis.adapters.postgres;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface PostgresConnectionProvider {
    Connection open() throws SQLException;
}
