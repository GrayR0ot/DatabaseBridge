package com.bgsoftware.common.databasebridge.logger;

public interface ILogger {

    void error(String message, Throwable error);

    boolean hasDebugEnabled();

    void debug(String message);

    void info(String message);

}
