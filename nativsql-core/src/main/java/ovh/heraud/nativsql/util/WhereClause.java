package ovh.heraud.nativsql.util;

import java.util.ArrayList;
import java.util.List;

import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Builder for SQL WHERE clauses.
 * Composes multiple conditions with AND logic.
 * Example: new WhereClause().add("status", Operator.EQUALS, "ACTIVE")
 *                           .add("role", Operator.EQUALS, "ADMIN")
 *                           .build(sb, converter) → appends "WHERE status = :status AND role = :role"
 */
public class WhereClause implements SQLBuilder {
    private final List<Condition> conditions = new ArrayList<>();
    private final List<ColumnCondition> columnConditions = new ArrayList<>();
    private final List<RangeCondition> rangeConditions = new ArrayList<>();
    private final List<CustomCondition> customConditions = new ArrayList<>();
    private String tablePrefix = "";
    private boolean hasJoins = false;
    private JoinResolver joinResolver = null;

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
     * Adds a column-only condition (e.g., IS NULL) to this WHERE clause.
     *
     * @param column   the column name (camelCase)
     * @param operator the column operator
     * @return this for method chaining
     */
    public WhereClause addColumnOperator(String column, ColumnOperator operator) {
        columnConditions.add(new ColumnCondition(column, operator));
        return this;
    }

    /**
     * Adds a range condition (e.g., BETWEEN) to this WHERE clause.
     *
     * @param column    the column name (camelCase)
     * @param operator  the range operator
     * @param paramLow  the parameter name for the lower bound
     * @param low       the lower bound value
     * @param paramHigh the parameter name for the upper bound
     * @param high      the upper bound value
     * @return this for method chaining
     */
    public WhereClause addRangeOperator(String column, RangeOperator operator,
            String paramLow, Object low, String paramHigh, Object high) {
        rangeConditions.add(new RangeCondition(column, operator, paramLow, low, paramHigh, high));
        return this;
    }

    /**
     * Adds a custom WHERE expression (e.g., "(address).city" for composite types).
     * Multiple calls accumulate — all expressions are combined with AND.
     *
     * @param expression the SQL expression (e.g., "(address).city")
     * @param paramName  the parameter name to bind the value to
     * @param value      the value to bind to the parameter
     * @return this for method chaining
     */
    public WhereClause custom(String expression, String paramName, Object value) {
        customConditions.add(new CustomCondition(expression, paramName, value));
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
     * Registers the resolver that translates dot-notation column paths
     * (e.g. "group.name") to fully-qualified SQL column references.
     * Must be set before {@link #build} is called when dot-notation columns are used.
     *
     * @param resolver the join resolver
     * @return this for method chaining
     */
    public WhereClause withJoinResolver(JoinResolver resolver) {
        this.joinResolver = resolver;
        return this;
    }

    /**
     * Checks if this WHERE clause has any conditions.
     *
     * @return true if there are no conditions of any kind and no custom expression
     */
    public boolean isEmpty() {
        return conditions.isEmpty()
                && columnConditions.isEmpty()
                && rangeConditions.isEmpty()
                && customConditions.isEmpty();
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
        List<String> conditionStrings = buildConditionStrings(identifierConverter);
        sb.append(String.join(" AND ", conditionStrings));
    }

    /**
     * Gets all standard conditions in this WHERE clause.
     *
     * @return a defensive copy of the conditions list
     */
    public List<Condition> getConditions() {
        return new ArrayList<>(conditions);
    }

    /**
     * Gets all column-only conditions in this WHERE clause.
     *
     * @return a defensive copy of the column conditions list
     */
    public List<ColumnCondition> getColumnConditions() {
        return new ArrayList<>(columnConditions);
    }

    /**
     * Gets all range conditions in this WHERE clause.
     *
     * @return a defensive copy of the range conditions list
     */
    public List<RangeCondition> getRangeConditions() {
        return new ArrayList<>(rangeConditions);
    }

    /**
     * Gets all custom WHERE expressions in this WHERE clause.
     *
     * @return a defensive copy of the custom conditions list
     */
    public List<CustomCondition> getCustomConditions() {
        return new ArrayList<>(customConditions);
    }

    /**
     * Builds the SQL WHERE clause with formatting (newlines and indentation).
     * Appends formatted conditions to the StringBuilder.
     *
     * @param sb                  the StringBuilder to append to
     * @param identifierConverter the converter for identifier transformation
     */
    public void buildFormatted(StringBuilder sb, IdentifierConverter identifierConverter) {
        if (isEmpty()) {
            return;
        }

        List<String> conditionStrings = buildConditionStrings(identifierConverter);
        for (int i = 0; i < conditionStrings.size(); i++) {
            sb.append("        ").append(conditionStrings.get(i));
            if (i < conditionStrings.size() - 1) {
                sb.append("\n    AND\n");
            }
        }
    }

    private List<String> buildConditionStrings(IdentifierConverter identifierConverter) {
        List<String> conditionStrings = new ArrayList<>();

        for (Condition condition : conditions) {
            String dbCol = toDbCol(identifierConverter, condition.getColumn());
            String paramName = SqlUtils.columnPathToParamName(condition.getColumn());
            conditionStrings.add(condition.getOperator().getExpressionBuilder().buildExpression(dbCol, paramName));
        }

        for (ColumnCondition cc : columnConditions) {
            String dbCol = toDbCol(identifierConverter, cc.getColumn());
            conditionStrings.add(cc.getOperator().getExpressionBuilder().buildExpression(dbCol));
        }

        for (RangeCondition rc : rangeConditions) {
            String dbCol = toDbCol(identifierConverter, rc.getColumn());
            conditionStrings.add(rc.getOperator().getExpressionBuilder()
                    .buildExpression(dbCol, rc.getParamLow(), rc.getParamHigh()));
        }

        for (CustomCondition cc : customConditions) {
            conditionStrings.add(cc.getExpression() + " = :" + cc.getParamName());
        }

        return conditionStrings;
    }

    private String toDbCol(IdentifierConverter identifierConverter, String column) {
        if (column.contains(".")) {
            if (joinResolver == null) {
                throw new NativSQLException(
                    "Dot-notation column paths are not supported in this query type: '" + column + "'");
            }
            return joinResolver.resolve(column, identifierConverter);
        }
        String dbCol = identifierConverter.toDB(column);
        if (hasJoins && !tablePrefix.isEmpty()) {
            dbCol = tablePrefix + "." + dbCol;
        }
        return dbCol;
    }
}
