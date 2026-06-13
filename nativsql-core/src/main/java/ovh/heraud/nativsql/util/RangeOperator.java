package ovh.heraud.nativsql.util;

/**
 * Enumeration of SQL range operators that require two bind parameters.
 * Example: BETWEEN :low AND :high.
 */
public enum RangeOperator {
    BETWEEN((col, low, high) -> col + " BETWEEN :" + low + " AND :" + high);

    private final RangeWhereExpressionBuilder expressionBuilder;

    RangeOperator(RangeWhereExpressionBuilder expressionBuilder) {
        this.expressionBuilder = expressionBuilder;
    }

    public RangeWhereExpressionBuilder getExpressionBuilder() {
        return expressionBuilder;
    }
}
