package com.bgsoftware.common.databasebridge;

import com.bgsoftware.common.databasebridge.session.IDatabaseSession;
import com.bgsoftware.common.databasebridge.sql.session.MariaDBDatabaseSession;
import com.bgsoftware.common.databasebridge.sql.session.MySQLDatabaseSession;
import com.bgsoftware.common.databasebridge.sql.session.PostgreSQLDatabaseSession;
import com.bgsoftware.common.databasebridge.sql.session.SQLiteDatabaseSession;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DatabaseSessionFactory {

    private static final Map<Class<? extends IDatabaseSession.Args>, DatabaseSessionCreator> CREATORS = initializeCreators();

    private DatabaseSessionFactory() {

    }

    public static IDatabaseSession createSession(IDatabaseSession.Args args) {
        return CREATORS.get(args.getClass()).create(args);
    }

    private static Map<Class<? extends IDatabaseSession.Args>, DatabaseSessionCreator> initializeCreators() {
        Map<Class<? extends IDatabaseSession.Args>, DatabaseSessionCreator> creatorsMap = new HashMap<>();

        creatorsMap.put(MariaDBDatabaseSession.Args.class, MariaDBDatabaseSession::createSession);
        creatorsMap.put(MySQLDatabaseSession.Args.class, MySQLDatabaseSession::createSession);
        creatorsMap.put(PostgreSQLDatabaseSession.Args.class, PostgreSQLDatabaseSession::createSession);
        creatorsMap.put(SQLiteDatabaseSession.Args.class, SQLiteDatabaseSession::createSession);

        return Collections.unmodifiableMap(creatorsMap);
    }

    private interface DatabaseSessionCreator {

        IDatabaseSession create(IDatabaseSession.Args args);

    }

}
