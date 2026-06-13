package ovh.heraud.nativsql.util;

/**
 * Represents a WHERE condition that applies a column-only operator (no bind parameter).
 * Examples: IS NULL, IS NOT NULL.
 */
public class ColumnCondition {
    private final String column;
    private final ColumnOperator operator;

    public ColumnCondition(String column, ColumnOperator operator) {
        this.column = column;
        this.operator = operator;
    }

    public String getColumn() {
        return column;
    }

    public ColumnOperator getOperator() {
        return operator;
    }
}
