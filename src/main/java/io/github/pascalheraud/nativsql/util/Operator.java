package io.github.pascalheraud.nativsql.util;

/**
 * Enumeration of SQL operators for WHERE conditions.
 */
public enum Operator {
    EQUALS("="),
    IN("IN");

    private final String sql;

    Operator(String sql) {
        this.sql = sql;
    }

    public String getSql() {
        return sql;
    }
}
