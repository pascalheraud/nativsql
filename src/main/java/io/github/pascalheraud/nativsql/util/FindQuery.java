package io.github.pascalheraud.nativsql.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.pascalheraud.nativsql.annotation.MappedBy;
import io.github.pascalheraud.nativsql.domain.Entity;
import io.github.pascalheraud.nativsql.repository.GenericRepository;
import org.springframework.lang.NonNull;

/**
 * Builder for complex SELECT queries with filtering, ordering, and
 * associations.
 * The repository is required and must be provided at instantiation.
 * Example:
 * FindQuery.of(userRepository)
 * .select("id", "name", "email")
 * .whereAndEquals("status", "ACTIVE")
 * .whereAndEquals("role", "ADMIN")
 * .orderByAsc("name")
 * .leftJoin("group", "id", "name")
 *
 * @param <T>  the entity type
 * @param <ID> the entity ID type
 */
public class FindQuery<T extends Entity<ID>, ID> {
    private final GenericRepository<T, ID> repository;

    private final List<String> columns = new ArrayList<>();
    private final OrderBy orderBy = new OrderBy();
    private final List<Association> associations = new ArrayList<>();
    private final List<Join> joins = new ArrayList<>();
    private final Map<String, Object> whereConditions = new HashMap<>();
    private String customWhereExpression;
    private String customParamName;

    /**
     * Creates a new FindQuery for the specified repository.
     * 
     * @param repository the repository to query (required)
     * @throws IllegalArgumentException if repository is null
     */
    private FindQuery(GenericRepository<T, ID> repository) {
        if (repository == null) {
            throw new IllegalArgumentException("Repository cannot be null");
        }
        this.repository = repository;
    }

    /**
     * Factory method to create a new FindQuery builder from a repository.
     * 
     * @param repository the repository to query (required)
     * @return a new FindQuery builder instance
     */
    public static <T extends Entity<ID>, ID> FindQuery<T, ID> of(GenericRepository<T, ID> repository) {
        return new FindQuery<>(repository);
    }

    /**
     * Adds column(s) to the SELECT clause.
     */
    public FindQuery<T, ID> select(String... cols) {
        columns.addAll(Arrays.asList(cols));
        return this;
    }

    /**
     * Adds column(s) to the SELECT clause from a list.
     */
    public FindQuery<T, ID> select(List<String> cols) {
        columns.addAll(cols);
        return this;
    }

    /**
     * Adds an ascending order by condition.
     */
    public FindQuery<T, ID> orderByAsc(String column) {
        orderBy.asc(column);
        return this;
    }

    /**
     * Adds a descending order by condition.
     */
    public FindQuery<T, ID> orderByDesc(String column) {
        orderBy.desc(column);
        return this;
    }

    /**
     * Sets the OrderBy builder for this query.
     */
    public FindQuery<T, ID> orderBy(OrderBy orderBy) {
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
    public FindQuery<T, ID> whereAndEquals(String column, Object value) {
        whereConditions.put(column, value);
        return this;
    }

    /**
     * Adds a custom WHERE expression (e.g., "(address).city" for composite types).
     * 
     * @param expression the SQL expression (e.g., "(address).city")
     * @param paramName  the parameter name to use in the query
     * @param value      the value to bind to the parameter
     */
    public FindQuery<T, ID> whereExpression(String expression, String paramName, Object value) {
        this.customWhereExpression = expression;
        this.customParamName = paramName;
        whereConditions.put(paramName, value);
        return this;
    }

    /**
     * Adds an association to load (OneToMany relationship).
     * 
     * @param associationName the property name of the association
     * @param columns         the columns to retrieve from the associated entity
     */
    public FindQuery<T, ID> associate(String associationName, String... columns) {
        associations.add(new Association(associationName, Arrays.asList(columns)));
        return this;
    }

    /**
     * Adds an association to load (OneToMany relationship).
     * 
     * @param associationName the property name of the association
     * @param columns         the columns to retrieve from the associated entity
     */
    public FindQuery<T, ID> associate(String associationName, List<String> columns) {
        associations.add(new Association(associationName, columns));
        return this;
    }

    /**
     * Adds a LEFT JOIN for a @MappedBy association (ToOne relationship).
     * The MappedBy annotation on the field contains the repository of the joined
     * entity.
     * 
     * @param associationName the property name of the association field
     * @param columns         the columns to retrieve from the joined entity
     */
    public FindQuery<T, ID> leftJoin(String associationName, String... columns) {
        FieldAccessor fieldAccessor = repository.getEntityFields().get(associationName);
        MappedBy mappedBy = fieldAccessor.getAnnotation(MappedBy.class);
        GenericRepository<?, ?> joinRepository = getRepositoryInstance(mappedBy.repository());
        joins.add(new Join(associationName, Arrays.asList(columns), true, joinRepository));
        return this;
    }

    /**
     * Adds a LEFT JOIN for a @MappedBy association (ToOne relationship).
     * The MappedBy annotation on the field contains the repository of the joined
     * entity.
     *
     * @param associationName the property name of the association field
     * @param columns         the columns to retrieve from the joined entity
     */
    public FindQuery<T, ID> leftJoin(String associationName, List<String> columns) {
        FieldAccessor fieldAccessor = repository.getEntityFields().get(associationName);
        MappedBy mappedBy = fieldAccessor.getAnnotation(MappedBy.class);
        GenericRepository<?, ?> joinRepository = getRepositoryInstance(mappedBy.repository());
        joins.add(new Join(associationName, columns, true, joinRepository));
        return this;
    }

    /**
     * Adds an INNER JOIN for a @MappedBy association (ToOne relationship).
     * The MappedBy annotation on the field contains the repository of the joined
     * entity.
     *
     * @param associationName the property name of the association field
     * @param columns         the columns to retrieve from the joined entity
     */
    public FindQuery<T, ID> innerJoin(String associationName, String... columns) {
        FieldAccessor fieldAccessor = repository.getEntityFields().get(associationName);
        MappedBy mappedBy = fieldAccessor.getAnnotation(MappedBy.class);
        GenericRepository<?, ?> joinRepository = getRepositoryInstance(mappedBy.repository());
        joins.add(new Join(associationName, Arrays.asList(columns), false, joinRepository));
        return this;
    }

    /**
     * Adds an INNER JOIN for a @MappedBy association (ToOne relationship).
     * The MappedBy annotation on the field contains the repository of the joined
     * entity.
     *
     * @param associationName the property name of the association field
     * @param columns         the columns to retrieve from the joined entity
     */
    public FindQuery<T, ID> innerJoin(String associationName, List<String> columns) {
        FieldAccessor fieldAccessor = repository.getEntityFields().get(associationName);
        MappedBy mappedBy = fieldAccessor.getAnnotation(MappedBy.class);
        GenericRepository<?, ?> joinRepository = getRepositoryInstance(mappedBy.repository());
        joins.add(new Join(associationName, columns, false, joinRepository));
        return this;
    }

    /**
     * Gets a repository instance from its class.
     */
    private GenericRepository<?, ?> getRepositoryInstance(Class<?> repositoryClass) {
        try {
            return (GenericRepository<?, ?>) repositoryClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate repository: " + repositoryClass.getName(), e);
        }
    }

    /**
     * Gets the table name.
     */
    public String getTableName() {
        return repository.getTableNameForQuery();
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
     * Gets the associations to load (OneToMany).
     */
    public List<Association> getAssociations() {
        return new ArrayList<>(associations);
    }

    /**
     * Gets the joins (JOINs for @MappedBy associations).
     */
    public List<Join> getJoins() {
        return new ArrayList<>(joins);
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
     * Checks if there are any LEFT JOINs.
     */
    public boolean hasJoins() {
        return !joins.isEmpty();
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
     * 
     * @return the complete SQL query string
     */
    @NonNull
    public String buildSql() {
        String tableName = repository.getTableNameForQuery();

        // If there are joins, prefix columns with table name to avoid ambiguity
        // and include columns from joined tables
        List<String> prefixedColumns = new ArrayList<>();

        if (hasJoins()) {
            // Add main table columns with prefix
            for (String col : columns) {
                String snakeCaseCol = StringUtils.camelToSnake(col);
                prefixedColumns.add(tableName + "." + snakeCaseCol);
            }

            // Add joined table columns with their property name as prefix in aliases
            for (Join join : joins) {
                String joinTableName = join.getRepository().getTableNameForQuery();
                String propertyName = join.getName(); // Use the property name (e.g., "group")
                for (String col : join.getColumns()) {
                    String snakeCaseCol = StringUtils.camelToSnake(col);
                    // Use alias with property name prefix (e.g., group_id, group_name)
                    prefixedColumns.add(joinTableName + "." + snakeCaseCol + " AS " +
                                      propertyName + "_" + snakeCaseCol);
                }
            }
        } else {
            // No joins - use simple column listing
            for (String col : columns) {
                prefixedColumns.add(StringUtils.camelToSnake(col));
            }
        }

        String columnList = String.join(", ", prefixedColumns);

        StringBuilder sql = new StringBuilder("SELECT " + columnList + " FROM " + tableName);

        // Add JOINs for @MappedBy associations
        if (hasJoins()) {
            Fields entityFields = repository.getEntityFields();
            for (Join join : joins) {
                FieldAccessor fieldAccessor = entityFields.get(join.getName());
                if (fieldAccessor.hasAnnotation(MappedBy.class)) {
                    MappedBy mappedBy = fieldAccessor.getAnnotation(MappedBy.class);
                    String foreignKeyColumn = StringUtils.camelToSnake(mappedBy.value());
                    String joinTableName = join.getRepository().getTableNameForQuery();
                    String joinType = join.isLeftJoin() ? "LEFT JOIN" : "INNER JOIN";
                    sql.append(String.format(" %s %s ON %s.%s = %s.id",
                            joinType, joinTableName, tableName, foreignKeyColumn, joinTableName));
                }
            }
        }

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
                    // If there are joins, prefix the column with table name to avoid ambiguity
                    if (hasJoins()) {
                        columnName = tableName + "." + columnName;
                    }
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
     * 
     * @param valueConverter a function to convert values to SQL format
     * @return a map of parameter names to SQL values
     */
    @NonNull
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
