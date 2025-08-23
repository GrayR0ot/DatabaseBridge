package com.bgsoftware.common.databasebridge.transaction;

import com.google.common.base.Preconditions;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DatabaseTransactionsExecutor {

    private static final List<DatabaseTransactionsExecutor> ACTIVE_EXECUTORS = new LinkedList<>();

    private final BlockingQueue<PendingTransaction> pendingTransactions = new LinkedBlockingQueue<>();
    private final AtomicBoolean IS_RUNNING = new AtomicBoolean(true);

    private final ITransactionProcessor processor;
    private final Thread transactionsHandlerThread;


    public DatabaseTransactionsExecutor(ITransactionProcessor processor, ThreadFactory threadFactory) {
        this.processor = processor;
        this.transactionsHandlerThread = threadFactory.newThread(this::transactionsHandler);
        this.transactionsHandlerThread.start();
        ACTIVE_EXECUTORS.add(this);
    }

    public static void stopActiveExecutors() {
        ACTIVE_EXECUTORS.forEach(DatabaseTransactionsExecutor::stop);
        ACTIVE_EXECUTORS.clear();
    }

    public CompletableFuture<Void> addTransaction(IDatabaseTransaction transaction) {
        Preconditions.checkState(IS_RUNNING.get(), "Database Executor is not running");
        return addPendingTransaction(new PendingTransaction(transaction));
    }

    public CompletableFuture<Void> addTransactions(Collection<IDatabaseTransaction> transactions) {
        Preconditions.checkState(IS_RUNNING.get(), "Database Executor is not running");

        if (transactions.isEmpty())
            return CompletableFuture.completedFuture(null);

        return addPendingTransaction(new PendingTransaction(new MultipleDatabaseTransactions(transactions)));
    }

    public CompletableFuture<Void> addTransactions(IDatabaseTransaction... transactions) {
        Preconditions.checkState(IS_RUNNING.get(), "Database Executor is not running");

        if (transactions.length == 0)
            return CompletableFuture.completedFuture(null);

        if (transactions.length == 1)
            return addTransaction(transactions[0]);

        List<IDatabaseTransaction> transactionList = new LinkedList<>();
        Collections.addAll(transactionList, transactions);
        return addTransactions(transactionList);
    }

    private CompletableFuture<Void> addPendingTransaction(PendingTransaction pendingTransaction) {
        pendingTransactions.add(pendingTransaction);
        return pendingTransaction.waitable;
    }

    private void stop() {
        IS_RUNNING.set(false);
        try {
            this.transactionsHandlerThread.join();
        } catch (InterruptedException ignored) {
        }
    }

    private void transactionsHandler() {
        while (IS_RUNNING.get()) {
            try {
                handleNextTransactionSafe();
            } catch (Throwable error) {
                error.printStackTrace();
            }
        }

        // Handle all pending transactions
        while (!pendingTransactions.isEmpty())
            handleNextTransactionSafe();
    }

    private void handleNextTransactionSafe() {
        PendingTransaction transaction;
        try {
            transaction = pendingTransactions.poll(1, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            return;
        }

        if (transaction != null) {
            processTransaction(transaction);
        }
    }

    private void processTransaction(PendingTransaction pendingTransaction) {
        IDatabaseTransaction transaction = pendingTransaction.transaction;
        if (transaction instanceof MultipleDatabaseTransactions) {
            for (IDatabaseTransaction innerTransaction : ((MultipleDatabaseTransactions) transaction).getTransactions())
                processor.processTransaction(innerTransaction);
        } else {
            processor.processTransaction(transaction);
        }
        pendingTransaction.waitable.complete(null);
    }

    private static class MultipleDatabaseTransactions implements IDatabaseTransaction {

        private final List<IDatabaseTransaction> transactions;

        MultipleDatabaseTransactions(Collection<IDatabaseTransaction> transactions) {
            this.transactions = new LinkedList<>(transactions);
        }

        List<IDatabaseTransaction> getTransactions() {
            return transactions;
        }

    }

    private static class PendingTransaction {

        private final IDatabaseTransaction transaction;
        private final CompletableFuture<Void> waitable;

        PendingTransaction(IDatabaseTransaction transaction) {
            this.transaction = transaction;
            this.waitable = new CompletableFuture<>();
        }

    }


}
