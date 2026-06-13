package ovh.heraud.nativsql.util;

/**
 * Strategy interface for building SQL range expressions with two bind parameters
 * (e.g., BETWEEN :low AND :high).
 */
@FunctionalInterface
public interface RangeWhereExpressionBuilder {
    String buildExpression(String column, String paramLow, String paramHigh);
}
