package relay;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** One SQLite connection per transaction. Writers reserve their transaction before reading. */
final class Database {
    private final String url;

    Database(Path file) {
        Path absolute = file.toAbsolutePath();
        try {
            Files.createDirectories(absolute.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create the database directory", e);
        }
        url = "jdbc:sqlite:" + absolute;
    }

    @FunctionalInterface
    interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    <T> T transaction(boolean write, Work<T> work) {
        try (Connection c = DriverManager.getConnection(url); Statement control = c.createStatement()) {
            control.execute("PRAGMA foreign_keys=ON");
            control.execute("PRAGMA busy_timeout=15000");
            control.execute(write ? "BEGIN IMMEDIATE" : "BEGIN");
            try {
                T result = work.run(c);
                control.execute("COMMIT");
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    control.execute("ROLLBACK");
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            }
        } catch (SQLException e) {
            if ((e.getErrorCode() & 255) == 19) {
                throw new Problem(409, "A record with those details already exists or a referenced record is missing.");
            }
            System.err.println("SQLite operation failed: " + e.getMessage());
            throw new Problem(503, "The database is unavailable. Try again shortly.");
        }
    }

    private static void bind(PreparedStatement query, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            query.setObject(i + 1, values[i]);
        }
    }

    private static Map<String,Object> readRow(ResultSet result) throws SQLException {
        var row = new LinkedHashMap<String,Object>();
        ResultSetMetaData metadata = result.getMetaData();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            row.put(metadata.getColumnLabel(i), result.getObject(i));
        }
        return row;
    }

    static List<Map<String,Object>> rows(Connection c, String sql, Object... values) throws SQLException {
        try (var query = c.prepareStatement(sql)) {
            bind(query, values);
            try (var result = query.executeQuery()) {
                var records = new ArrayList<Map<String,Object>>();
                while (result.next()) records.add(readRow(result));
                return records;
            }
        }
    }

    static Map<String,Object> one(Connection c, String sql, Object... values) throws SQLException {
        try (var query = c.prepareStatement(sql)) {
            bind(query, values);
            try (var result = query.executeQuery()) {
                return result.next() ? readRow(result) : null;
            }
        }
    }

    static int exec(Connection c, String sql, Object... values) throws SQLException {
        try (var query = c.prepareStatement(sql)) {
            bind(query, values);
            return query.executeUpdate();
        }
    }

    static int insert(Connection c, String sql, Object... values) throws SQLException {
        exec(c, sql, values);
        return ((Number) one(c, "SELECT last_insert_rowid() AS id").get("id")).intValue();
    }
}
