package ovh.heraud.nativsql.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.repository.GenericRepository;
import ovh.heraud.nativsql.util.ReflectionUtils.Getter;

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
 * @param <T>  the entity type implementing IEntity
 * @param <ID> the entity ID type
 */
public class FindQuery<T extends IEntity<ID>, ID> extends WhereQuery<T, ID, FindQuery<T, ID>> {
    private static final String INDENT = "    ";

    private final List<String> columns = new ArrayList<>();
    private final List<ExpressionColumn> expressionColumns = new ArrayList<>();
    private final OrderBy orderBy = new OrderBy();
    private final List<Association> associations = new ArrayList<>();
    private final List<Join> joins = new ArrayList<>();
    private Integer limit = null;
    private Integer offset = null;

    /**
     * Creates a new FindQuery for the specified repository.
     *
     * @param repository the repository to query (required)
     */
    private FindQuery(GenericRepository<T, ID> repository) {
        super(repository);
    }

    /**
     * Factory method to create a new FindQuery builder from a repository.
     *
     * @param repository the repository to query (required)
     * @return a new FindQuery builder instance
     */
    public static <T extends IEntity<ID>, ID> FindQuery<T, ID> of(
            GenericRepository<T, ID> repository) {
        return new FindQuery<>(repository);
    }

    /**
     * Limits the number of rows returned.
     * Generates FETCH FIRST n ROWS ONLY (or FETCH NEXT n ROWS ONLY when combined with offset).
     *
     * @param n the maximum number of rows (must be > 0)
     * @throws NativSQLException if n <= 0
     */
    public FindQuery<T, ID> limit(int n) {
        if (n <= 0) {
            throw new NativSQLException("limit must be greater than 0");
        }
        this.limit = n;
        return this;
    }

    /**
     * Skips the first n rows before returning results.
     * Generates OFFSET n ROWS.
     * Must be combined with an ORDER BY for deterministic results.
     *
     * @param n the number of rows to skip (must be >= 0)
     * @throws NativSQLException if n < 0
     */
    public FindQuery<T, ID> offset(int n) {
        if (n < 0) {
            throw new NativSQLException("offset must be greater than or equal to 0");
        }
        this.offset = n;
        return this;
    }

    /**
     * Adds column(s) to the SELECT clause.
     *
     * @param cols the columns to select (must not be empty)
     * @throws NativSQLException if cols is empty
     */
    public FindQuery<T, ID> select(String... cols) {
        if (cols == null || cols.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
        for (String col : cols) {
            boolean collidesWithExpression = expressionColumns.stream()
                    .anyMatch(e -> e.getAlias().equals(col));
            if (collidesWithExpression) {
                throw new NativSQLException(
                        "Column '" + col + "' collides with a selectExpression(...) alias of the same name");
            }
        }
        columns.addAll(Arrays.asList(cols));
        return this;
    }

    /**
     * Adds column(s) to the SELECT clause using getter method references.
     *
     * @param getters the getter method references (e.g., User::getId,
     *                User::getName)
     */
    @SafeVarargs
    public final FindQuery<T, ID> select(Getter<T>... getters) {
        return select(ReflectionUtils.getColumnNames(getters));
    }

    /**
     * Adds a raw SQL expression to the SELECT clause, aliased with {@code AS "alias"}.
     * The literal token {@code {{table}}}, if present in {@code sqlExpression}, is
     * substituted with this query's table name when the SQL is built.
     *
     * @param alias         the column alias (must not be null or blank)
     * @param sqlExpression the raw SQL expression (must not be null or blank)
     * @throws NativSQLException if alias/sqlExpression is blank, if alias is already
     *                            used by another expression or by a plain select(...)
     *                            column
     */
    public FindQuery<T, ID> selectExpression(String alias, String sqlExpression) {
        return selectExpression(alias, sqlExpression, Map.of());
    }

    /**
     * Adds a raw SQL expression to the SELECT clause, aliased with {@code AS "alias"},
     * with named parameters merged into the query's parameter map.
     *
     * @param alias         the column alias (must not be null or blank)
     * @param sqlExpression the raw SQL expression (must not be null or blank)
     * @param params        named parameters referenced by the SQL expression
     * @throws NativSQLException if alias/sqlExpression is blank, if alias is already
     *                            used by another expression or by a plain select(...)
     *                            column
     */
    public FindQuery<T, ID> selectExpression(String alias, String sqlExpression, Map<String, Object> params) {
        if (alias == null || alias.isBlank()) {
            throw new NativSQLException("Expression alias cannot be null or blank");
        }
        if (sqlExpression == null || sqlExpression.isBlank()) {
            throw new NativSQLException("Expression SQL cannot be null or blank");
        }
        boolean duplicateAlias = expressionColumns.stream().anyMatch(e -> e.getAlias().equals(alias));
        if (duplicateAlias) {
            throw new NativSQLException("Duplicate expression alias '" + alias + "'");
        }
        if (columns.contains(alias)) {
            throw new NativSQLException(
                    "Expression alias '" + alias + "' collides with a plain select(...) column of the same name");
        }
        expressionColumns.add(new ExpressionColumn(alias, sqlExpression, params == null ? Map.of() : params));
        return this;
    }

    /**
     * Adds a raw SQL expression to the SELECT clause using a getter method reference
     * to derive the alias.
     *
     * @param aliasGetter   the getter method reference used only to derive the alias
     *                      (e.g. ClientReport::getOrderCount)
     * @param sqlExpression the raw SQL expression (must not be null or blank)
     */
    public <R> FindQuery<T, ID> selectExpression(Getter<R> aliasGetter, String sqlExpression) {
        return selectExpression(ReflectionUtils.getColumnName(aliasGetter), sqlExpression);
    }

    /**
     * Adds a raw SQL expression to the SELECT clause using a getter method reference
     * to derive the alias, with named parameters merged into the query's parameter
     * map.
     *
     * @param aliasGetter   the getter method reference used only to derive the alias
     *                      (e.g. ClientReport::getOrderCount)
     * @param sqlExpression the raw SQL expression (must not be null or blank)
     * @param params        named parameters referenced by the SQL expression
     */
    public <R> FindQuery<T, ID> selectExpression(Getter<R> aliasGetter, String sqlExpression,
            Map<String, Object> params) {
        return selectExpression(ReflectionUtils.getColumnName(aliasGetter), sqlExpression, params);
    }

    /**
     * Adds an ascending order by condition.
     */
    public FindQuery<T, ID> orderByAsc(String column) {
        orderBy.asc(column);
        return this;
    }

    /**
     * Adds an ascending order by condition using a getter method reference.
     *
     * @param getter the getter method reference (e.g., User::getFirstName)
     */
    public FindQuery<T, ID> orderByAsc(Getter<T> getter) {
        return orderByAsc(ReflectionUtils.getColumnName(getter));
    }

    /**
     * Adds a descending order by condition.
     */
    public FindQuery<T, ID> orderByDesc(String column) {
        orderBy.desc(column);
        return this;
    }

    /**
     * Adds a descending order by condition using a getter method reference.
     *
     * @param getter the getter method reference (e.g., User::getFirstName)
     */
    public FindQuery<T, ID> orderByDesc(Getter<T> getter) {
        return orderByDesc(ReflectionUtils.getColumnName(getter));
    }

    /**
     * Merges order conditions from another OrderBy builder into this query's
     * ordering.
     * This is an efficient way to apply pre-configured ordering without duplicating
     * logic.
     *
     * @param orderBy the OrderBy builder containing the order conditions to merge
     * @return this FindQuery for method chaining
     */
    public FindQuery<T, ID> orderBy(OrderBy orderBy) {
        this.orderBy.copyFrom(orderBy);
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
     * Adds an association to load (OneToMany relationship) using a getter method
     * reference.
     *
     * @param getter  the getter method reference for the association field (e.g.,
     *                User::getContacts)
     * @param columns the columns to retrieve from the associated entity
     */
    public FindQuery<T, ID> associate(Getter<T> getter, String... columns) {
        return associate(ReflectionUtils.getColumnName(getter), columns);
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
        FieldAccessor<?> fieldAccessor = repository.getEntityFields().get(associationName);
        MappedByInfo mappedByInfo = annotationManager.getMappedByInfo(fieldAccessor);
        if (mappedByInfo == null) {
            throw new NativSQLException(
                    "Field '" + associationName + "' is not annotated with @MappedBy. Cannot perform join.");
        }
        GenericRepository<?, ?> joinRepository = getRepositoryInstance(mappedByInfo.getRepositoryClass());
        joins.add(new Join(associationName, Arrays.asList(columns), true, joinRepository));
        return this;
    }

    /**
     * Adds a LEFT JOIN for a @MappedBy association using a getter method reference.
     *
     * @param getter  the getter method reference for the association field (e.g.,
     *                User::getGroup)
     * @param columns the columns to retrieve from the joined entity
     */
    public FindQuery<T, ID> leftJoin(Getter<T> getter, String... columns) {
        return leftJoin(ReflectionUtils.getColumnName(getter), columns);
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
        FieldAccessor<?> fieldAccessor = repository.getEntityFields().get(associationName);
        MappedByInfo mappedByInfo = annotationManager.getMappedByInfo(fieldAccessor);
        if (mappedByInfo == null) {
            throw new NativSQLException(
                    "Field '" + associationName + "' is not annotated with @MappedBy. Cannot perform join.");
        }
        GenericRepository<?, ?> joinRepository = getRepositoryInstance(mappedByInfo.getRepositoryClass());
        joins.add(new Join(associationName, Arrays.asList(columns), false, joinRepository));
        return this;
    }

    /**
     * Adds an INNER JOIN for a @MappedBy association using a getter method
     * reference.
     *
     * @param getter  the getter method reference for the association field (e.g.,
     *                User::getGroup)
     * @param columns the columns to retrieve from the joined entity
     */
    public FindQuery<T, ID> innerJoin(Getter<T> getter, String... columns) {
        return innerJoin(ReflectionUtils.getColumnName(getter), columns);
    }

    /**
     * Resolves a dot-notation column path to a fully-qualified SQL column reference.
     * Looks up the association name in the registered joins and uses its repository's
     * table name as the column prefix.
     *
     * @param path      the dot-notation path (e.g. "group.name")
     * @param converter the identifier converter for column name transformation
     * @return the fully-qualified SQL column reference (e.g. "user_group.name")
     * @throws NativSQLException if no join is registered for the association name
     */
    private String resolveJoinColumn(String path, IdentifierConverter converter) {
        String[] segments = path.split("\\.", 2);
        String associationName = segments[0];
        String column = segments[1];
        Join join = joins.stream()
                .filter(j -> j.getName().equals(associationName))
                .findFirst()
                .orElseThrow(() -> new NativSQLException(
                        "No join found for association '" + associationName
                        + "' in dot-notation column '" + path + "'. "
                        + "Add leftJoin/innerJoin before using this column in a WHERE condition."));
        return join.getRepository().getTableName() + "." + converter.toDB(column);
    }

    /**
     * Gets a repository instance from its class.
     */
    private GenericRepository<?, ?> getRepositoryInstance(Class<?> repositoryClass) {
        try {
            Object result = repositoryClass.getConstructor().newInstance();
            if (result == null) {
                throw new NativSQLException("Failed to instantiate repository: " + repositoryClass.getName());
            }
            return (GenericRepository<?, ?>) result;
        } catch (Exception e) {
            throw new NativSQLException("Failed to instantiate repository: " + repositoryClass.getName(), e);
        }
    }

    /**
     * Gets the table name.
     */
    public String getTableName() {
        return repository.getTableName();
    }

    /**
     * Gets the associations to load (OneToMany).
     */
    public List<Association> getAssociations() {
        return new ArrayList<>(associations);
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

    @Override
    public void build(StringBuilder sb, IdentifierConverter identifierConverter) {
        buildSql(sb, identifierConverter);
    }

    /**
     * Gets the parameters map for the SQL query, merging named parameters from
     * selectExpression(...) on top of the WHERE-based parameters.
     *
     * @return a map of parameter names to values
     * @throws NativSQLException if an expression parameter name collides with an
     *                            existing parameter name
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = super.getParameters();
        for (ExpressionColumn ec : expressionColumns) {
            for (Map.Entry<String, Object> entry : ec.getParams().entrySet()) {
                if (params.containsKey(entry.getKey())) {
                    throw new NativSQLException("Duplicate parameter name '" + entry.getKey() + "'");
                }
                params.put(entry.getKey(), entry.getValue());
            }
        }
        return params;
    }

    /**
     * Builds the SQL SELECT query and returns it as a String.
     * This is a convenience method that creates a StringBuilder internally.
     *
     * @param identifierConverter the identifier converter to use for name
     *                            transformation
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
     * @param identifierConverter the identifier converter
     * @param tableName           the table name
     * @param column              the column name in Java naming
     * @param aliasPrefix         the alias prefix (same as column for main table, or
     *                            property name for joined table)
     * @param aliasSuffix         the alias suffix (null for main table, column name for
     *                            joined table)
     * @return the column expression with alias
     */
    private String buildColumnExpression(IdentifierConverter identifierConverter, String tableName,
            String column, String aliasPrefix, String aliasSuffix) {
        String dbColumn = identifierConverter.toDB(column);
        String alias = aliasSuffix == null ? aliasPrefix : aliasPrefix + "." + aliasSuffix;
        return String.format("""
                %s.%s AS "%s"
                """.strip(), tableName, dbColumn, alias);
    }

    /**
     * Builds the list of columns with proper prefixes and aliases for the SELECT
     * clause.
     * Handles both simple cases and cases with joins.
     *
     * @param identifierConverter the identifier converter to use for name
     *                            transformation
     * @param tableName           the main table name
     * @return a list of column expressions ready for the SELECT clause
     */
    private List<String> buildPrefixedColumns(IdentifierConverter identifierConverter, String tableName) {
        List<String> prefixedColumns = new ArrayList<>();

        for (String col : columns) {
            if (col == null || col.isEmpty()) {
                throw new NativSQLException("Column name cannot be null or empty");
            }
            String columnWithAlias = buildColumnExpression(identifierConverter, tableName, col, col, null);
            prefixedColumns.add(columnWithAlias);
        }

        for (Join join : joins) {
            String joinTableName = join.getRepository().getTableName();
            String propertyName = join.getName();
            for (String col : join.getColumns()) {
                if (col == null || col.isEmpty()) {
                    throw new NativSQLException("Column name cannot be null or empty");
                }
                String columnWithAlias = buildColumnExpression(identifierConverter, joinTableName, col, propertyName, col);
                prefixedColumns.add(columnWithAlias);
            }
        }

        for (ExpressionColumn ec : expressionColumns) {
            String resolvedSql = ec.getSql().replace("{{table}}", tableName);
            prefixedColumns.add(String.format("%s AS \"%s\"", resolvedSql, ec.getAlias()));
        }

        return prefixedColumns;
    }

    /**
     * Builds the SQL SELECT query.
     * Uses the columns and table name stored in this FindQuery.
     * Appends the complete SQL query to the provided StringBuilder.
     *
     * @param sb                  the StringBuilder to append the SQL to
     * @param identifierConverter the identifier converter to use for name
     *                            transformation
     */
    private void buildSql(StringBuilder sb, IdentifierConverter identifierConverter) {
        if (columns.isEmpty() && expressionColumns.isEmpty() && joins.isEmpty()) {
            throw new NativSQLException("At least one column must be selected");
        }
        String tableName = repository.getTableName();
        List<String> prefixedColumns = buildPrefixedColumns(identifierConverter, tableName);

        sb.append("SELECT\n");
        for (int i = 0; i < prefixedColumns.size(); i++) {
            sb.append(INDENT).append(prefixedColumns.get(i));
            if (i < prefixedColumns.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("FROM ").append(tableName);

        if (hasJoins()) {
            Fields entityFields = repository.getEntityFields();
            for (Join join : joins) {
                FieldAccessor<?> fieldAccessor = entityFields.get(join.getName());
                MappedByInfo mappedByInfo = annotationManager.getMappedByInfo(fieldAccessor);
                if (mappedByInfo != null) {
                    String foreignKeyColumn = identifierConverter.toDB(mappedByInfo.getForeignKeyProperty());
                    String joinTableName = join.getRepository().getTableName();
                    String joinKeyword = join.isLeftJoin() ? "LEFT" : "INNER";
                    sb.append(String.format("\n      %s JOIN %s ON %s.%s = %s.id",
                            joinKeyword, joinTableName, tableName, foreignKeyColumn, joinTableName));
                }
            }
        }

        if (hasWhereConditions()) {
            whereClause.withJoinResolver(this::resolveJoinColumn).withTablePrefix(tableName).withJoins(hasJoins());
            sb.append("\nWHERE\n");
            whereClause.buildFormatted(sb, identifierConverter);
        }

        if (!orderBy.isEmpty()) {
            sb.append("\nORDER BY\n");
            orderBy.buildFormatted(sb, identifierConverter);
        }

        if (offset != null && offset > 0) {
            sb.append("\nOFFSET ").append(offset).append(" ROWS");
        }
        if (limit != null) {
            String fetchKeyword = (offset != null && offset > 0) ? "NEXT" : "FIRST";
            sb.append("\nFETCH ").append(fetchKeyword).append(" ").append(limit).append(" ROWS ONLY");
        }

        sb.append("\n");
    }

    /**
     * Functional interface for converting values to SQL format.
     */
    @FunctionalInterface
    public interface ValueConverter {
        Object convert(Object value);
    }
}
