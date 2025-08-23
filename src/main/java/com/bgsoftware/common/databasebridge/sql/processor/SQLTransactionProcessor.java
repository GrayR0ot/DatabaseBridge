package com.bgsoftware.common.databasebridge.sql.processor;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.common.databasebridge.logger.ILogger;
import com.bgsoftware.common.databasebridge.sql.query.QueryResult;
import com.bgsoftware.common.databasebridge.sql.session.SQLDatabaseSession;
import com.bgsoftware.common.databasebridge.sql.transaction.SQLDatabaseTransaction;
import com.bgsoftware.common.databasebridge.transaction.IDatabaseTransaction;
import com.bgsoftware.common.databasebridge.transaction.ITransactionProcessor;
import com.google.common.base.Preconditions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLTransactionProcessor implements ITransactionProcessor {

    private static final Pattern QUERY_VALUE_PATTERN = Pattern.compile("\\?");

    private final SQLDatabaseSession<?> session;
    private final ILogger logger;

    public SQLTransactionProcessor(SQLDatabaseSession<?> session, ILogger logger) {
        this.session = session;
        this.logger = logger;
    }

    @Override
    public void processTransaction(IDatabaseTransaction transaction) {
        Preconditions.checkArgument(transaction instanceof SQLDatabaseTransaction,
                "Transaction is not SQL transaction: " + transaction);

        SQLDatabaseTransaction<?> sqlTransaction = (SQLDatabaseTransaction<?>) transaction;

        String query = sqlTransaction.buildQuery();

        QueryString fullQuery = new QueryString(query);

        executeQueryInternal(query, new QueryResult<PreparedStatement>().onSuccess(preparedStatement -> {
            List<SQLDatabaseTransaction.DatabaseValues> allBatchValues = sqlTransaction.getValues();
            if (allBatchValues.size() > 1) {
                executeBatchTransaction(preparedStatement, allBatchValues, query);
            } else if (allBatchValues.size() == 1) {
                executeTransaction(preparedStatement, allBatchValues.iterator().next(), query);
            }
        }).onFail(error -> {
            this.logger.error("An unexpected error occurred while executing query `" + fullQuery.query + "`:", error);
        }));
    }

    private void executeBatchTransaction(PreparedStatement preparedStatement,
                                         List<SQLDatabaseTransaction.DatabaseValues> allBatchValues,
                                         String query) throws SQLException {
        QueryString fullQuery = this.logger.hasDebugEnabled() ? new QueryString(query) : null;

        Connection connection = preparedStatement.getConnection();

        try {
            connection.setAutoCommit(false);

            for (SQLDatabaseTransaction.DatabaseValues batchValues : allBatchValues) {
                if (fullQuery != null)
                    fullQuery.query = query;

                populateStatement(preparedStatement, batchValues, fullQuery);

                if (fullQuery != null)
                    this.logger.debug(fullQuery.query);

                preparedStatement.addBatch();
            }

            preparedStatement.executeBatch();

            try {
                connection.commit();
            } catch (Throwable ignored) {
            }
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void executeTransaction(PreparedStatement preparedStatement,
                                    SQLDatabaseTransaction.DatabaseValues values,
                                    String query) throws SQLException {
        QueryString fullQuery = this.logger.hasDebugEnabled() ? new QueryString(query) : null;

        populateStatement(preparedStatement, values, fullQuery);

        if (fullQuery != null)
            this.logger.debug(fullQuery.query);

        preparedStatement.executeUpdate();
    }

    private void executeQueryInternal(String query, QueryResult<PreparedStatement> callback) {
        this.session.waitForConnection();
        this.session.customQuery(query, callback);
    }

    private static void populateStatement(PreparedStatement preparedStatement,
                                          SQLDatabaseTransaction.DatabaseValues values,
                                          @Nullable QueryString fullQuery) throws SQLException {
        Counter index = new Counter();
        for (Object value : values.values) {
            addObject(preparedStatement, index, value, fullQuery);
        }
    }

    private static void addObject(PreparedStatement preparedStatement, Counter index, Object value,
                                  @Nullable QueryString fullQuery) throws SQLException {
        int curr = index.count;
        preparedStatement.setObject(curr, value);
        ++index.count;
        if (fullQuery != null) {
            fullQuery.query = QUERY_VALUE_PATTERN.matcher(fullQuery.query)
                    .replaceFirst(Matcher.quoteReplacement(value + ""));
        }
    }

    private static class Counter {

        private int count = 1;

    }

    private static class QueryString {

        private String query;

        QueryString(String query) {
            this.query = query;
        }

    }

}
