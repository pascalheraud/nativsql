package ovh.heraud.nativsql.util;

import java.util.ArrayList;
import java.util.List;

import ovh.heraud.nativsql.db.IdentifierConverter;

/**
 * Builder for SQL WHERE clauses.
 * Composes multiple conditions with AND logic.
 * Example: new WhereClause().add("status", Operator.EQUALS, "ACTIVE")
 *                           .add("role", Operator.EQUALS, "ADMIN")
 *                           .build(sb, converter) → appends "WHERE status = :status AND role = :role"
 */
public class WhereClause implements SQLBuilder {
    private final List<Condition> conditions = new ArrayList<>();
    private String customExpression;
    private String customParamName;
    private String tablePrefix = "";
    private boolean hasJoins = false;

    /**
     * Adds a condition to this WHERE clause.
     *
     * @param column   the column name (camelCase)
     * @param operator the comparison operator
     * @param value    the value to compare
     * @return this for method chaining
     */
    public WhereClause add(String column, Operator operator, Object value) {
        conditions.add(new Condition(column, operator, value));
        return this;
    }

    /**
     * Adds a custom WHERE expression instead of standard conditions.
     * The custom expression takes precedence over standard conditions.
     *
     * @param expression the custom SQL expression (e.g., "(address).city")
     * @param paramName  the parameter name to bind the value to
     * @return this for method chaining
     */
    public WhereClause custom(String expression, String paramName) {
        this.customExpression = expression;
        this.customParamName = paramName;
        return this;
    }

    /**
     * Sets the table prefix for column names (e.g., "user_table").
     * Used when there are JOINs to avoid column ambiguity.
     *
     * @param tablePrefix the table name to prefix columns with
     * @return this for method chaining
     */
    public WhereClause withTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
        return this;
    }

    /**
     * Sets whether there are JOINs in the query.
     * When true, columns will be prefixed with the table name.
     *
     * @param hasJoins whether the query has JOINs
     * @return this for method chaining
     */
    public WhereClause withJoins(boolean hasJoins) {
        this.hasJoins = hasJoins;
        return this;
    }

    /**
     * Checks if this WHERE clause has any conditions.
     *
     * @return true if there are conditions or a custom expression
     */
    public boolean isEmpty() {
        return conditions.isEmpty() && customExpression == null;
    }

    /**
     * Builds the SQL WHERE clause.
     * Appends " WHERE ..." to the StringBuilder if conditions exist.
     *
     * @param sb                  the StringBuilder to append to
     * @param identifierConverter the converter for identifier transformation
     */
    @Override
    public void build(StringBuilder sb, IdentifierConverter identifierConverter) {
        if (isEmpty()) {
            return;
        }

        sb.append(" WHERE ");

        if (customExpression != null) {
            // Use custom expression with parameter binding
            sb.append(customExpression).append(" = :").append(customParamName);
        } else {
            // Compose standard conditions with AND logic
            List<String> conditionStrings = new ArrayList<>();
            for (Condition condition : conditions) {
                String dbCol = identifierConverter.toDB(condition.getColumn());

                // Prefix with table name if there are JOINs
                if (hasJoins && !tablePrefix.isEmpty()) {
                    dbCol = tablePrefix + "." + dbCol;
                }

                String paramName = condition.getColumn();
                String conditionStr = condition.getOperator()
                        .getExpressionBuilder()
                        .buildExpression(dbCol, paramName);
                conditionStrings.add(conditionStr);
            }

            sb.append(String.join(" AND ", conditionStrings));
        }
    }

    /**
     * Gets all conditions in this WHERE clause.
     *
     * @return a copy of the conditions list
     */
    public List<Condition> getConditions() {
        return new ArrayList<>(conditions);
    }

    /**
     * Gets the custom expression if set.
     *
     * @return the custom expression or null
     */
    public String getCustomExpression() {
        return customExpression;
    }

    /**
     * Gets the custom parameter name if set.
     *
     * @return the custom parameter name or null
     */
    public String getCustomParamName() {
        return customParamName;
    }
}
