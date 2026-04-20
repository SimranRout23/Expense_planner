package com.expenseplanner.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DBConnection.java
 * Provides a PostgreSQL JDBC connection.
 * Update DB_URL, DB_USER, DB_PASSWORD to match your setup.
 */
public class DBConnection {

    // ── Change these to match your PostgreSQL setup ──────────────────
    private static final String DB_URL      = System.getenv("DB_URL");
    private static final String DB_USER     = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
    // ─────────────────────────────────────────────────────────────────

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("PostgreSQL JDBC driver not found: " + e.getMessage());
        }
    }

    /**
     * Returns a new JDBC Connection. Caller is responsible for closing it.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}
