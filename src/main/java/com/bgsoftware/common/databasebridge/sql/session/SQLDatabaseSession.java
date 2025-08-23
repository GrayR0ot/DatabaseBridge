package com.bgsoftware.common.databasebridge.sql.session;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.common.databasebridge.logger.ILogger;
import com.bgsoftware.common.databasebridge.session.BaseDatabaseSession;
import com.bgsoftware.common.databasebridge.sql.processor.SQLTransactionProcessor;
import com.bgsoftware.common.databasebridge.sql.query.Column;
import com.bgsoftware.common.databasebridge.sql.query.QueryResult;
import com.bgsoftware.common.databasebridge.transaction.DatabaseTransactionsExecutor;
import com.google.common.base.Preconditions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public abstract class SQLDatabaseSession<A extends SQLDatabaseSession.Args> extends BaseDatabaseSession<A> {

    protected final CompletableFuture<Void> ready = new CompletableFuture<>();

    @Nullable
    protected DataSource dataSource;
    protected boolean logging;

    protected SQLDatabaseSession(A args) {
        super(args);

        SQLTransactionProcessor processor = new SQLTransactionProcessor(this, args.getLogger());
        DatabaseTransactionsExecutor executor = new DatabaseTransactionsExecutor(processor, args.getThreadFactory());
        setExecutor(executor);
    }

    public void setLogging(boolean logging) {
        this.logging = logging;
    }

    public void close() {
        Preconditions.checkNotNull(this.dataSource, "Session was not initialized.");

        try {
            ((AutoCloseable) this.dataSource).close();
        } catch (Exception error) {
            this.args.getLogger().error("An unexpected error occurred while closing connection:", error);
        }
    }

    public void waitForConnection() {
        try {
            ready.get();
        } catch (Exception error) {
            this.args.getLogger().error("An unexpected error occurred while waiting for connection:", error);
        }
    }

    public void createTable(String tableName, Column[] columns, QueryResult<Void> queryResult) {
        StringBuilder columnsSection = new StringBuilder();
        for (Column column : columns) {
            columnsSection.append(",")
                    .append(column.getName())
                    .append(" ")
                    .append(column.getValue());
        }

        executeUpdate(String.format("CREATE TABLE IF NOT EXISTS %s%s (%s);",
                this.args.getDbPrefix(), tableName, columnsSection.substring(1)), queryResult);
    }

    public void renameTable(String tableName, String newName, QueryResult<Void> queryResult) {
        String prefix = this.args.getDbPrefix();
        executeUpdate(String.format("RENAME TABLE %s%s TO %s%s;", prefix, tableName, prefix, newName), queryResult);
    }

    public void createIndex(String indexName, String tableName, String[] columns, QueryResult<Void> queryResult) {
        StringBuilder columnsSection = new StringBuilder();
        for (String column : columns) {
            columnsSection.append(",").append(column);
        }

        executeUpdate(String.format("CREATE UNIQUE INDEX %s ON %s%s (%s);",
                indexName, this.args.getDbPrefix(), tableName, columnsSection.substring(1)), queryResult);
    }

    public void modifyColumnType(String tableName, String columnName, String newType, QueryResult<Void> queryResult) {
        executeUpdate(String.format("ALTER TABLE %s%s MODIFY COLUMN %s %s;",
                this.args.getDbPrefix(), tableName, columnName, newType), queryResult);
    }

    public void addColumn(String tableName, String columnName, String type, QueryResult<Void> queryResult) {
        executeUpdate(String.format("ALTER TABLE %s%s ADD COLUMN %s %s;",
                this.args.getDbPrefix(), tableName, columnName, type), queryResult);
    }

    public void removePrimaryKey(String tableName, String columnName, QueryResult<Void> queryResult) {
        executeUpdate(String.format("ALTER TABLE %s%s DROP PRIMARY KEY;", this.args.getDbPrefix(), tableName), queryResult);
    }

    public void select(String tableName, String filters, QueryResult<ResultSet> queryResult) {
        executeQuery(String.format("SELECT * FROM %s%s%s;", this.args.getDbPrefix(), tableName, filters), queryResult);
    }

    public void setJournalMode(String journalMode, QueryResult<ResultSet> queryResult) {
        executeQuery(String.format("PRAGMA journal_mode=%s;", journalMode), queryResult);
    }

    public void customQuery(String statement, QueryResult<PreparedStatement> queryResult) {
        Preconditions.checkNotNull(this.dataSource, "Session was not initialized.");

        String query = replaceQueryInternal(statement);

        if (this.args.getLogger().hasDebugEnabled())
            this.args.getLogger().debug(query);

        try {
            prepareStatementInternal(query, queryResult::complete);
        } catch (SQLException error) {
            queryResult.fail(error);
        }
    }

    public void executeUpdate(String statement, QueryResult<Void> queryResult) {
        Preconditions.checkNotNull(this.dataSource, "Session was not initialized.");

        String query = replaceQueryInternal(statement);

        if (this.args.getLogger().hasDebugEnabled())
            this.args.getLogger().debug(query);

        try {
            prepareStatementInternal(query, preparedStatement -> {
                preparedStatement.executeUpdate();
                queryResult.complete(null);
            });
        } catch (SQLException error) {
            queryResult.fail(error);
        }
    }

    public void executeQuery(String statement, QueryResult<ResultSet> queryResult) {
        Preconditions.checkNotNull(this.dataSource, "Session was not initialized.");

        String query = replaceQueryInternal(statement);

        if (this.args.getLogger().hasDebugEnabled())
            this.args.getLogger().debug(query);

        try {
            prepareStatementInternal(query, preparedStatement -> {
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    queryResult.complete(resultSet);
                }
            });
        } catch (SQLException error) {
            queryResult.fail(error);
        }
    }

    private String replaceQueryInternal(String statement) {
        return statement
                .replace("{prefix}", this.args.getDbPrefix())
                .replace("BIG_DECIMAL", "TEXT")
                .replace("DECIMAL", "DECIMAL(10, 2)")
                .replace("UUID", "VARCHAR(36)")
                .replace("LONG_UNIQUE_TEXT", "VARCHAR(255)")
                .replace("UNIQUE_TEXT", "VARCHAR(30)");
    }

    protected void prepareStatementInternal(String query, PreparedStatementCallback callback) throws SQLException {
        try (Connection conn = this.dataSource.getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(query)) {
            callback.accept(preparedStatement);
        }
    }

    protected interface PreparedStatementCallback {

        void accept(PreparedStatement preparedStatement) throws SQLException;

    }

    public static abstract class Args extends BaseDatabaseSession.Args {

        private final String dbPrefix;

        protected Args(String dbPrefix, String databaseThreadName, ILogger logger) {
            super(databaseThreadName, logger);
            this.dbPrefix = dbPrefix;
        }

        public String getDbPrefix() {
            return this.dbPrefix;
        }

    }

}
