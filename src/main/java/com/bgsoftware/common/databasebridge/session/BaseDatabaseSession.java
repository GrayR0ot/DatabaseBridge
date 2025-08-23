package com.bgsoftware.common.databasebridge.session;

import com.bgsoftware.common.databasebridge.logger.ILogger;
import com.bgsoftware.common.databasebridge.transaction.DatabaseTransactionsExecutor;
import com.bgsoftware.common.databasebridge.transaction.IDatabaseTransaction;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;

public abstract class BaseDatabaseSession<A extends BaseDatabaseSession.Args> implements IDatabaseSession {

    protected DatabaseTransactionsExecutor executor;
    protected final A args;

    protected BaseDatabaseSession(A args) {
        this.args = args;
    }

    protected void setExecutor(DatabaseTransactionsExecutor executor) {
        this.executor = executor;
    }

    public abstract boolean connect();

    public CompletableFuture<Void> execute(IDatabaseTransaction transaction) {
        return this.executor.addTransaction(transaction);
    }

    public CompletableFuture<Void> execute(IDatabaseTransaction... transactions) {
        return this.executor.addTransactions(transactions);
    }

    public CompletableFuture<Void> execute(Collection<IDatabaseTransaction> transactions) {
        return this.executor.addTransactions(transactions);
    }

    public static abstract class Args implements IDatabaseSession.Args {

        private final ThreadFactory threadFactory;
        private final ILogger logger;

        protected Args(String databaseThreadName, ILogger logger) {
            this.threadFactory = new ThreadFactoryBuilder().setNameFormat(databaseThreadName).build();
            this.logger = logger;
        }

        @Override
        public ThreadFactory getThreadFactory() {
            return this.threadFactory;
        }

        @Override
        public ILogger getLogger() {
            return this.logger;
        }

    }

}
