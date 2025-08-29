package com.bgsoftware.common.databasebridge.sql.session;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.common.databasebridge.logger.ILogger;
import com.bgsoftware.common.databasebridge.session.IDatabaseSession;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SQLiteDatabaseSession extends SQLDatabaseSession<SQLiteDatabaseSession.Args> {

    public static SQLiteDatabaseSession createSession(IDatabaseSession.Args args) {
        if (!(args instanceof Args)) {
            throw new IllegalArgumentException("Must create session with valid args");
        }

        return new SQLiteDatabaseSession((Args) args);
    }

    private SQLiteDatabaseSession(Args args) {
        super(args);
    }

    @Override
    public boolean connect() {
        if (this.logging)
            this.args.getLogger().info("Trying to connect to local database (SQLite)...");

        File file = args.databaseFile;

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                if (!file.createNewFile()) {
                    this.args.getLogger().error("Failed to create SQLite database file.", new Exception());
                    return false;
                }
            } catch (IOException error) {
                this.args.getLogger().error("An unexpected error occurred while creating the database file:", error);
                return false;
            }
        }

        String jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath().replace("\\", "/");

        try {
            Class.forName("org.sqlite.JDBC");

            this.dataSource = new SQLiteHikariDataSource(DriverManager.getConnection(jdbcUrl));

            if (logging)
                this.args.getLogger().info("Successfully established connection with local database!");

            ready.complete(null);

            return true;
        } catch (Exception error) {
            this.args.getLogger().error("An unexpected error occurred while connecting to SQLite database:", error);
        }

        return false;
    }

    @Override
    protected void prepareStatementInternal(String query, PreparedStatementCallback callback) throws SQLException {
        try (PreparedStatement preparedStatement = this.dataSource.getConnection().prepareStatement(query)) {
            callback.accept(preparedStatement);
        }
    }

    private static class SQLiteHikariDataSource extends HikariDataSource {

        @Nullable
        private Connection connection;

        private SQLiteHikariDataSource(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (this.connection == null || this.connection.isClosed())
                throw new SQLException("HikariDataSource " + this + " has been closed.");

            return this.connection;
        }

        @Override
        public void close() {
            try {
                this.connection.close();
            } catch (Throwable error) {
                throw new RuntimeException(error);
            } finally {
                this.connection = null;
            }
        }

    }

    public static class Args extends SQLDatabaseSession.Args {

        private final File databaseFile;

        public Args(File databaseFile, String databaseThreadName, ILogger logger) {
            super("", databaseThreadName, logger);
            this.databaseFile = databaseFile;
        }

    }

}
