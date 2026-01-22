package ovh.heraud.nativsql.util;

/**
 * Enumeration of SQL operators for WHERE conditions.
 * Each operator defines how to generate its SQL expression.
 */
public enum Operator {
    EQUALS("=", (col, param) -> col + " = :" + param),
    IN("IN", (col, param) -> col + " IN (:" + param + ")");

    private final String sql;
    private final WhereExpressionBuilder expressionBuilder;

    Operator(String sql, WhereExpressionBuilder expressionBuilder) {
        this.sql = sql;
        this.expressionBuilder = expressionBuilder;
    }

    public String getSql() {
        return sql;
    }

    public WhereExpressionBuilder getExpressionBuilder() {
        return expressionBuilder;
    }
}
