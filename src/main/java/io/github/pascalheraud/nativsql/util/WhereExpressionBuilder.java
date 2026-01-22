package io.github.pascalheraud.nativsql.util;

/**
 * Functional interface for building WHERE clause expressions.
 * Each operator can define how its SQL expression should be generated.
 */
@FunctionalInterface
public interface WhereExpressionBuilder {
    /**
     * Builds a WHERE condition expression for the given column and parameter.
     *
     * @param dbColumn the database column name (already converted to DB identifier)
     * @param paramName the parameter name (for binding)
     * @return the SQL expression (e.g., "user_id = :userId" or "status IN (:status)")
     */
    String buildExpression(String dbColumn, String paramName);
}
