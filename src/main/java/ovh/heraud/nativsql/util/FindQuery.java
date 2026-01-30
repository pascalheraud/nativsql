package ovh.heraud.nativsql.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import ovh.heraud.nativsql.annotation.MappedBy;
import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.domain.Entity;
import ovh.heraud.nativsql.repository.GenericRepository;

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
public class FindQuery<T extends Entity<ID>, ID> implements SQLBuilder {
    private final GenericRepository<T, ID> repository;

    private final List<String> columns = new ArrayList<>();
    private final OrderBy orderBy = new OrderBy();
    private final WhereClause whereClause = new WhereClause();
    private final List<Association> associations = new ArrayList<>();
    private final List<Join> joins = new ArrayList<>();

    /**
     * Creates a new FindQuery for the specified repository.
     *
     * @param repository the repository to query (required)
     * @param identifierConverter the identifier converter for name transformation
     * @throws IllegalArgumentException if repository or converter is null
     */
    private FindQuery(@NonNull GenericRepository<T, ID> repository) {
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
    /**
     * Merges order conditions from another OrderBy builder into this query's ordering.
     * This is an efficient way to apply pre-configured ordering without duplicating logic.
     *
     * @param orderBy the OrderBy builder containing the order conditions to merge
     * @return this FindQuery for method chaining
     */
    public FindQuery<T, ID> orderBy(OrderBy orderBy) {
        this.orderBy.copyFrom(orderBy);
        return this;
    }

    /**
     * Adds a WHERE condition with EQUALS operator (property = value).
     */
    public FindQuery<T, ID> whereAndEquals(String column, Object value) {
        whereClause.add(column, Operator.EQUALS, value);
        return this;
    }

    /**
     * Adds a WHERE condition with IN operator (property IN (...)).
     */
    public FindQuery<T, ID> whereAndIn(String column, List<?> values) {
        whereClause.add(column, Operator.IN, values);
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
        whereClause.custom(expression, paramName);
        whereClause.add(paramName, Operator.EQUALS, value);
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
    public List<Condition> getWhereConditions() {
        return whereClause.getConditions();
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
        return !whereClause.isEmpty();
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

    @Override
    public void build(StringBuilder sb, IdentifierConverter identifierConverter) {
        buildSql(sb, identifierConverter);
    }

    /**
     * Builds the SQL SELECT query and returns it as a String.
     * This is a convenience method that creates a StringBuilder internally.
     *
     * @param identifierConverter the identifier converter to use for name transformation
     * @return the complete SQL query string
     */
    public String buildString(IdentifierConverter identifierConverter) {
        StringBuilder sb = new StringBuilder();
        buildSql(sb, identifierConverter);
        return sb.toString();
    }

    /**
     * Builds a column expression with table prefix and alias.
     * For main table columns: buildColumnExpression("user", "id", "id", null)
     * For joined table columns: buildColumnExpression("group", "id", "group", "id")
     *
     * @param tableName the table name
     * @param column the column name in Java naming
     * @param aliasPrefix the alias prefix (same as column for main table, or property name for joined table)
     * @param aliasSuffix the alias suffix (null for main table, column name for joined table)
     * @return the column expression with alias
     */
    private String buildColumnExpression(IdentifierConverter identifierConverter, String tableName, String column, String aliasPrefix, String aliasSuffix) {
        String dbColumn = identifierConverter.toDB(column);
        String alias = aliasSuffix == null ? aliasPrefix : aliasPrefix + "." + aliasSuffix;
        return String.format("""
                %s.%s AS "%s"
                """.strip(), tableName, dbColumn, alias);
    }

    /**
     * Builds the list of columns with proper prefixes and aliases for the SELECT clause.
     * Handles both simple cases and cases with joins.
     *
     * @param identifierConverter the identifier converter to use for name transformation
     * @param tableName the main table name
     * @return a list of column expressions ready for the SELECT clause
     */
    private List<String> buildPrefixedColumns(IdentifierConverter identifierConverter, String tableName) {
        List<String> prefixedColumns = new ArrayList<>();

        // Add main table columns with table prefix and alias
        for (String col : columns) {
            String columnWithAlias = buildColumnExpression(identifierConverter, tableName, col, col, null);
            prefixedColumns.add(columnWithAlias);
        }

        // Add joined table columns with their property name as prefix in aliases
        for (Join join : joins) {
            String joinTableName = join.getRepository().getTableNameForQuery();
            String propertyName = join.getName(); // Use the property name (e.g., "group")
            for (String col : join.getColumns()) {
                String columnWithAlias = buildColumnExpression(identifierConverter, joinTableName, col, propertyName, col);
                prefixedColumns.add(columnWithAlias);
            }
        }

        return prefixedColumns;
    }

    /**
     * Builds the SQL SELECT query.
     * Uses the columns and table name stored in this FindQuery.
     * Appends the complete SQL query to the provided StringBuilder.
     *
     * @param sb                  the StringBuilder to append the SQL to
     * @param identifierConverter the identifier converter to use for name transformation
     */
    private void buildSql(StringBuilder sb, IdentifierConverter identifierConverter) {
        String tableName = repository.getTableNameForQuery();
        List<String> prefixedColumns = buildPrefixedColumns(identifierConverter, tableName);

        String columnList = String.join(", ", prefixedColumns);

        sb.append("SELECT ").append(columnList).append(" FROM ").append(tableName);

        // Add JOINs for @MappedBy associations
        if (hasJoins()) {
            Fields entityFields = repository.getEntityFields();
            for (Join join : joins) {
                FieldAccessor fieldAccessor = entityFields.get(join.getName());
                if (fieldAccessor.hasAnnotation(MappedBy.class)) {
                    MappedBy mappedBy = fieldAccessor.getAnnotation(MappedBy.class);
                    String foreignKeyColumn = identifierConverter.toDB(mappedBy.value());
                    String joinTableName = join.getRepository().getTableNameForQuery();
                    String joinKeyword = join.isLeftJoin() ? "LEFT" : "INNER";
                    sb.append(String.format(" %s JOIN %s ON %s.%s = %s.id",
                            joinKeyword, joinTableName, tableName, foreignKeyColumn, joinTableName));
                }
            }
        }

        // Add WHERE clause
        if (hasWhereConditions()) {
            whereClause.withTablePrefix(tableName).withJoins(hasJoins());
            whereClause.build(sb, identifierConverter);
        }

        // Add ORDER BY clause
        if (!orderBy.isEmpty()) {
            sb.append(" ");
            orderBy.build(sb, identifierConverter);
        }
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
        for (Condition condition : whereClause.getConditions()) {
            Object sqlValue = valueConverter.convert(condition.getValue());
            params.put(condition.getColumn(), sqlValue);
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
