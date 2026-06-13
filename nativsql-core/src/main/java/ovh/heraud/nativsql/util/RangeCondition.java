package ovh.heraud.nativsql.util;

/**
 * Represents a WHERE range condition with two bind parameters.
 * Example: column BETWEEN :low AND :high.
 */
public class RangeCondition {
    private final String column;
    private final RangeOperator operator;
    private final String paramLow;
    private final Object low;
    private final String paramHigh;
    private final Object high;

    public RangeCondition(String column, RangeOperator operator,
            String paramLow, Object low, String paramHigh, Object high) {
        this.column = column;
        this.operator = operator;
        this.paramLow = paramLow;
        this.low = low;
        this.paramHigh = paramHigh;
        this.high = high;
    }

    public String getColumn() {
        return column;
    }

    public RangeOperator getOperator() {
        return operator;
    }

    public String getParamLow() {
        return paramLow;
    }

    public Object getLow() {
        return low;
    }

    public String getParamHigh() {
        return paramHigh;
    }

    public Object getHigh() {
        return high;
    }
}
