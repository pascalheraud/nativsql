package ovh.heraud.nativsql.util;

/**
 * Enumeration of SQL operators that apply to a column without a bind parameter.
 * Examples: IS NULL, IS NOT NULL.
 */
public enum ColumnOperator {
    IS_NULL(col -> col + " IS NULL"),
    IS_NOT_NULL(col -> col + " IS NOT NULL");

    private final ColumnWhereExpressionBuilder expressionBuilder;

    ColumnOperator(ColumnWhereExpressionBuilder expressionBuilder) {
        this.expressionBuilder = expressionBuilder;
    }

    public ColumnWhereExpressionBuilder getExpressionBuilder() {
        return expressionBuilder;
    }
}
