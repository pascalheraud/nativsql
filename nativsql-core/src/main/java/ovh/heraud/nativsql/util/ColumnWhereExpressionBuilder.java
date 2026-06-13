package ovh.heraud.nativsql.util;

/**
 * Strategy interface for building SQL expressions that operate on a column only,
 * without a bind parameter (e.g., IS NULL, IS NOT NULL).
 */
@FunctionalInterface
public interface ColumnWhereExpressionBuilder {
    String buildExpression(String column);
}
