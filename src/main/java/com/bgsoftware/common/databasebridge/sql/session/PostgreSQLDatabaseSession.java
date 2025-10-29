package com.bgsoftware.common.databasebridge.sql.session;

import com.bgsoftware.common.databasebridge.logger.ILogger;
import com.bgsoftware.common.databasebridge.session.IDatabaseSession;
import com.bgsoftware.common.databasebridge.sql.query.QueryResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.ResultSet;

public class PostgreSQLDatabaseSession extends SQLDatabaseSession<PostgreSQLDatabaseSession.Args> {

    public static PostgreSQLDatabaseSession createSession(IDatabaseSession.Args args) {
        if (!(args instanceof Args)) {
            throw new IllegalArgumentException("Must create session with valid args");
        }

        return new PostgreSQLDatabaseSession((Args) args);
    }

    private PostgreSQLDatabaseSession(Args args) {
        super(args);
    }

    @Override
    public boolean connect() {
        if (this.logging)
            this.args.getLogger().info("Trying to connect to remote database (PostgreSQL)...");

        try {
            HikariConfig config = new HikariConfig();
            config.setConnectionTestQuery("SELECT 1");
            config.setPoolName("Database Pool");

            config.setDriverClassName("org.postgresql.Driver");

            config.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                    args.address, args.port, args.dbName, args.sslMode));
            config.setUsername(args.userName);
            config.setPassword(args.password);
            config.setMinimumIdle(5);
            config.setMaximumPoolSize(50);
            config.setConnectionTimeout(10000);
            config.setIdleTimeout(args.waitTimeout);
            config.setMaxLifetime(args.maxLifetime);
            config.addDataSourceProperty("characterEncoding", "utf8");
            config.addDataSourceProperty("useUnicode", "true");

            this.dataSource = new HikariDataSource(config);

            if (this.logging)
                this.args.getLogger().info("Successfully established connection with remote database!");

            this.ready.complete(null);

            return true;
        } catch (Throwable error) {
            this.args.getLogger().error("An unexpected error occurred while connecting to the MariaDB database:", error);
        }

        return false;
    }

    @Override
    public void setJournalMode(String journalMode, QueryResult<ResultSet> queryResult) {
        queryResult.fail(new UnsupportedOperationException("Cannot change journal mode in maria-db"));
    }

    public static class Args extends SQLDatabaseSession.Args {

        private final String address;
        private final String dbName;
        private final String userName;
        private final String password;
        private final int port;
        private final String sslMode;
        private final long waitTimeout;
        private final long maxLifetime;

        public Args(String address, int port, String dbName, String userName, String password, String prefix,
                    String sslMode, long waitTimeout, long maxLifetime,
                    String databaseThreadName, ILogger logger) {
            super(prefix, databaseThreadName, logger);
            this.address = address;
            this.dbName = dbName;
            this.userName = userName;
            this.password = password;
            this.port = port;
            this.sslMode = sslMode;
            this.waitTimeout = waitTimeout;
            this.maxLifetime = maxLifetime;
        }

    }

}
