package com.bgsoftware.common.databasebridge.session;

import com.bgsoftware.common.databasebridge.logger.ILogger;
import com.bgsoftware.common.databasebridge.transaction.IDatabaseTransaction;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;

public interface IDatabaseSession {

    boolean connect();

    void close();

    CompletableFuture<Void> execute(IDatabaseTransaction transaction);

    CompletableFuture<Void> execute(IDatabaseTransaction... transactions);

    CompletableFuture<Void> execute(Collection<IDatabaseTransaction> transactions);

    interface Args {

        ThreadFactory getThreadFactory();

        ILogger getLogger();

    }

}
