package ovh.heraud.nativsql.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
public class FindQuery<T extends IEntity<ID>, ID> extends AbstractWhereQuery<T, ID, FindQuery<T, ID>> {
    private static final String INDENT = "    ";

    private final List<String> columns = new ArrayList<>();
    private final OrderBy orderBy = new OrderBy();
    private final List<Association> associations = new ArrayList<>();
    private final List<Join> joins = new ArrayList<>();

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
     * Adds column(s) to the SELECT clause.
     *
     * @param cols the columns to select (must not be empty)
     * @throws NativSQLException if cols is empty
     */
    public FindQuery<T, ID> select(String... cols) {
        if (cols == null || cols.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
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
     * Adds column(s) to the SELECT clause from a list.
     *
     * @param cols the columns to select (must not be empty)
     * @throws NativSQLException if cols is null or empty
     */
    public FindQuery<T, ID> select(List<String> cols) {
        if (cols == null || cols.isEmpty()) {
            throw new NativSQLException("Column list cannot be empty");
        }
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
     * Adds an association to load (OneToMany relationship) using a getter method
     * reference.
     *
     * @param getter  the getter method reference for the association field (e.g.,
     *                User::getContacts)
     * @param columns the columns to retrieve from the associated entity
     */
    public FindQuery<T, ID> associate(Getter<T> getter, String... columns) {
        return associate(ReflectionUtils.getColumnName(getter), Arrays.asList(columns));
    }

    /**
     * Adds an association to load (OneToMany relationship) using a getter method
     * reference.
     *
     * @param getter  the getter method reference for the association field (e.g.,
     *                User::getContacts)
     * @param columns the columns to retrieve from the associated entity
     */
    public FindQuery<T, ID> associate(Getter<T> getter, List<String> columns) {
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
     * Adds a LEFT JOIN for a @MappedBy association (ToOne relationship).
     * The MappedBy annotation on the field contains the repository of the joined
     * entity.
     *
     * @param associationName the property name of the association field
     * @param columns         the columns to retrieve from the joined entity
     */
    public FindQuery<T, ID> leftJoin(String associationName, List<String> columns) {
        FieldAccessor<?> fieldAccessor = repository.getEntityFields().get(associationName);
        MappedByInfo mappedByInfo = annotationManager.getMappedByInfo(fieldAccessor);
        if (mappedByInfo == null) {
            throw new NativSQLException(
                    "Field '" + associationName + "' is not annotated with @MappedBy. Cannot perform join.");
        }
        GenericRepository<?, ?> joinRepository = getRepositoryInstance(mappedByInfo.getRepositoryClass());
        joins.add(new Join(associationName, columns, true, joinRepository));
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
     * Adds a LEFT JOIN for a @MappedBy association using a getter method reference.
     *
     * @param getter  the getter method reference for the association field (e.g.,
     *                User::getGroup)
     * @param columns the columns to retrieve from the joined entity
     */
    public FindQuery<T, ID> leftJoin(Getter<T> getter, List<String> columns) {
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
     * Adds an INNER JOIN for a @MappedBy association (ToOne relationship).
     * The MappedBy annotation on the field contains the repository of the joined
     * entity.
     *
     * @param associationName the property name of the association field
     * @param columns         the columns to retrieve from the joined entity
     */
    public FindQuery<T, ID> innerJoin(String associationName, List<String> columns) {
        FieldAccessor<?> fieldAccessor = repository.getEntityFields().get(associationName);
        MappedByInfo mappedByInfo = annotationManager.getMappedByInfo(fieldAccessor);
        if (mappedByInfo == null) {
            throw new NativSQLException(
                    "Field '" + associationName + "' is not annotated with @MappedBy. Cannot perform join.");
        }
        GenericRepository<?, ?> joinRepository = getRepositoryInstance(mappedByInfo.getRepositoryClass());
        joins.add(new Join(associationName, columns, false, joinRepository));
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
     * Adds an INNER JOIN for a @MappedBy association using a getter method
     * reference.
     *
     * @param getter  the getter method reference for the association field (e.g.,
     *                User::getGroup)
     * @param columns the columns to retrieve from the joined entity
     */
    public FindQuery<T, ID> innerJoin(Getter<T> getter, List<String> columns) {
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
     * Gets the selected columns.
     */
    public List<String> getColumns() {
        return new ArrayList<>(columns);
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
     * Gets an array of association names.
     */
    public String[] getAssociationNames() {
        return associations.stream()
                .map(Association::getName)
                .toArray(String[]::new);
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
