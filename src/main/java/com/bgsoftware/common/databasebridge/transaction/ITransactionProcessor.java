package com.bgsoftware.common.databasebridge.transaction;

public interface ITransactionProcessor {

    void processTransaction(IDatabaseTransaction transaction);

}
