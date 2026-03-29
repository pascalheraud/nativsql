package ovh.heraud.nativsql.util;

/**
 * Strategy for generating SQL WHERE condition expressions.
 *
 * This functional interface implements the Strategy pattern to allow different operators
 * to generate their SQL expressions differently. For example:
 * - EQUALS generates: "column = :paramName"
 * - IN generates: "column IN (:paramName)"
 *
 * Used in conjunction with the Operator enum, which holds both the operator name
 * and a WhereExpressionBuilder implementation for that operator.
 *
 * Example usage (via Operator enum):
 * Operator.EQUALS.getExpressionBuilder().buildExpression("user_id", "userId")
 *   → returns "user_id = :userId"
 *
 * Operator.IN.getExpressionBuilder().buildExpression("status", "status")
 *   → returns "status IN (:status)"
 */
@FunctionalInterface
public interface WhereExpressionBuilder {
    /**
     * Generates the SQL WHERE condition expression for this operator.
     *
     * Called by WhereClause when building the complete WHERE clause from multiple conditions.
     * Each operator implements this differently:
     * - EQUALS: generates "column = :paramName"
     * - IN: generates "column IN (:paramName)"
     *
     * Usage example (called internally by WhereClause):
     * {@code
     * Condition condition = new Condition("user_id", Operator.EQUALS, 123);
     * String sqlFragment = condition.getOperator()
     *     .getExpressionBuilder()
     *     .buildExpression("user_id", "userId");
     * // Returns: "user_id = :userId"
     * }
     *
     * @param dbColumn the database column name (already converted from Java naming to DB naming)
     * @param paramName the parameter name used for value binding (typically matches column name)
     * @return the SQL expression fragment (e.g., "user_id = :userId" or "status IN (:status)")
     */
    String buildExpression(String dbColumn, String paramName);
}
