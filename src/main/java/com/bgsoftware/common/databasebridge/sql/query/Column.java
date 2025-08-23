package com.bgsoftware.common.databasebridge.sql.query;

public class Column {

    private final String name;
    private final String value;

    public Column(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

}
