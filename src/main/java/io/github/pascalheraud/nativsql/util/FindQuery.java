package io.github.pascalheraud.nativsql.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.annotation.Nonnull;

/**
 * Builder for complex SELECT queries with filtering, ordering, and associations.
 * The table name is required and must be provided at instantiation.
 * Example:
 * new FindQuery("users")
 *   .select("id", "name", "email")
 *   .whereAndEquals("status", "ACTIVE")
 *   .whereAndEquals("role", "ADMIN")
 *   .orderByAsc("name")
 *   .join("addresses", "id", "street")
 */
public class FindQuery {
    private final String tableName;
    private final List<String> columns = new ArrayList<>();
    private final OrderBy orderBy = new OrderBy();
    private final List<Association> associations = new ArrayList<>();
    private final Map<String, Object> whereConditions = new HashMap<>();
    private String customWhereExpression;
    private String customParamName;

    /**
     * Creates a new FindQuery for the specified table.
     * @param tableName the name of the table to query (required)
     * @throws IllegalArgumentException if tableName is null or empty
     */
    private FindQuery(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        this.tableName = tableName;
    }

    /**
     * Factory method to create a new FindQuery builder for the specified table.
     * @param tableName the name of the table to query (required)
     * @return a new FindQuery builder instance
     * @throws IllegalArgumentException if tableName is null or empty
     */
    public static FindQuery of(String tableName) {
        return new FindQuery(tableName);
    }

    /**
     * Adds column(s) to the SELECT clause.
     */
    public FindQuery select(String... cols) {
        columns.addAll(Arrays.asList(cols));
        return this;
    }

    /**
     * Adds column(s) to the SELECT clause from a list.
     */
    public FindQuery select(List<String> cols) {
        columns.addAll(cols);
        return this;
    }

    /**
     * Adds an ascending order by condition.
     */
    public FindQuery orderByAsc(String column) {
        orderBy.asc(column);
        return this;
    }

    /**
     * Adds a descending order by condition.
     */
    public FindQuery orderByDesc(String column) {
        orderBy.desc(column);
        return this;
    }

    /**
     * Sets the OrderBy builder for this query.
     */
    public FindQuery orderBy(OrderBy orderBy) {
        // Copy the order conditions from the provided orderBy to this query's orderBy
        String orderByClause = orderBy.build();
        if (orderByClause.isEmpty()) {
            return this;
        }

        // Remove "ORDER BY " prefix
        if (orderByClause.toUpperCase().startsWith("ORDER BY ")) {
            orderByClause = orderByClause.substring(9);
        }

        for (String orderClause : orderByClause.split(",")) {
            String trimmed = orderClause.trim();
            if (trimmed.toUpperCase().endsWith("ASC")) {
                String column = trimmed.substring(0, trimmed.length() - 3).trim();
                this.orderBy.asc(column);
            } else if (trimmed.toUpperCase().endsWith("DESC")) {
                String column = trimmed.substring(0, trimmed.length() - 4).trim();
                this.orderBy.desc(column);
            }
        }
        return this;
    }

    /**
     * Adds a WHERE condition (property = value).
     */
    public FindQuery whereAndEquals(String column, Object value) {
        whereConditions.put(column, value);
        return this;
    }

    /**
     * Adds a custom WHERE expression (e.g., "(address).city" for composite types).
     * @param expression the SQL expression (e.g., "(address).city")
     * @param paramName the parameter name to use in the query
     * @param value the value to bind to the parameter
     */
    public FindQuery whereExpression(String expression, String paramName, Object value) {
        this.customWhereExpression = expression;
        this.customParamName = paramName;
        whereConditions.put(paramName, value);
        return this;
    }

    /**
     * Adds an association to load (OneToMany relationship).
     * @param associationName the property name of the association
     * @param columns the columns to retrieve from the associated entity
     */
    public FindQuery join(String associationName, String... columns) {
        associations.add(new Association(associationName, Arrays.asList(columns)));
        return this;
    }

    /**
     * Adds an association to load (OneToMany relationship).
     * @param associationName the property name of the association
     * @param columns the columns to retrieve from the associated entity
     */
    public FindQuery join(String associationName, List<String> columns) {
        associations.add(new Association(associationName, columns));
        return this;
    }

    /**
     * Gets the table name.
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Gets the selected columns.
     */
    public List<String> getColumns() {
        return new ArrayList<>(columns);
    }

    /**
     * Gets the ORDER BY clause builder.
     */
    public OrderBy getOrderBy() {
        return orderBy;
    }

    /**
     * Gets the associations to load.
     */
    public List<Association> getAssociations() {
        return new ArrayList<>(associations);
    }

    /**
     * Gets the WHERE conditions.
     */
    public Map<String, Object> getWhereConditions() {
        return new HashMap<>(whereConditions);
    }

    /**
     * Gets an array of column names.
     */
    public String[] getColumnsArray() {
        return columns.toArray(new String[0]);
    }

    /**
     * Gets an array of association names.
     */
    public String[] getAssociationNames() {
        return associations.stream()
                .map(Association::getName)
                .toArray(String[]::new);
    }

    /**
     * Gets column names for a specific association.
     */
    public String[] getAssociationColumns(String associationName) {
        return associations.stream()
                .filter(a -> a.getName().equals(associationName))
                .findFirst()
                .map(Association::getColumnsArray)
                .orElse(new String[0]);
    }

    /**
     * Checks if there are any WHERE conditions.
     */
    public boolean hasWhereConditions() {
        return !whereConditions.isEmpty();
    }

    /**
     * Checks if there are any associations to load.
     */
    public boolean hasAssociations() {
        return !associations.isEmpty();
    }

    /**
     * Checks if any columns are selected.
     */
    public boolean hasColumns() {
        return !columns.isEmpty();
    }

    /**
     * Builds the SQL SELECT query.
     * Uses the columns and table name stored in this FindQuery.
     * @return the complete SQL query string
     */
    @Nonnull
    public String buildSql() {
        // Build column list from stored columns using SqlUtils
        String columnList = SqlUtils.getColumnsList(columns.toArray(new String[0]));

        StringBuilder sql = new StringBuilder("SELECT " + columnList + " FROM " + tableName);

        // Add WHERE clause
        if (hasWhereConditions()) {
            sql.append(" WHERE ");

            // If there's a custom WHERE expression, use it instead of normal conditions
            if (customWhereExpression != null) {
                sql.append(customWhereExpression).append(" = :").append(customParamName);
            } else {
                List<String> conditions = new ArrayList<>();
                for (String column : whereConditions.keySet()) {
                    String columnName = StringUtils.camelToSnake(column);
                    conditions.add(columnName + " = :" + column);
                }
                sql.append(String.join(" AND ", conditions));
            }
        }

        // Add ORDER BY clause
        if (!orderBy.isEmpty()) {
            sql.append(" ").append(orderBy.build());
        }

        return Objects.requireNonNull(sql.toString());
    }

    /**
     * Gets the parameters map for the SQL query with converted values.
     * @param valueConverter a function to convert values to SQL format
     * @return a map of parameter names to SQL values
     */
    public Map<String, Object> getParameters(ValueConverter valueConverter) {
        Map<String, Object> params = new HashMap<>();
        for (Map.Entry<String, Object> entry : whereConditions.entrySet()) {
            Object sqlValue = valueConverter.convert(entry.getValue());
            params.put(entry.getKey(), sqlValue);
        }
        return params;
    }

    /**
     * Functional interface for converting values to SQL format.
     */
    @FunctionalInterface
    public interface ValueConverter {
        Object convert(Object value);
    }

}
