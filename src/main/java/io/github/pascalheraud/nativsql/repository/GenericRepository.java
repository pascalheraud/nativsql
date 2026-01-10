package io.github.pascalheraud.nativsql.repository;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.NonNull;

import io.github.pascalheraud.nativsql.annotation.OneToMany;
import io.github.pascalheraud.nativsql.domain.Entity;
import io.github.pascalheraud.nativsql.exception.SQLException;
import io.github.pascalheraud.nativsql.mapper.RowMapperFactory;
import io.github.pascalheraud.nativsql.mapper.TypeMapperFactory;
import io.github.pascalheraud.nativsql.util.FieldAccessor;
import io.github.pascalheraud.nativsql.util.FindQuery;
import io.github.pascalheraud.nativsql.util.OrderBy;
import io.github.pascalheraud.nativsql.util.ReflectionUtils;
import io.github.pascalheraud.nativsql.util.SqlUtils;
import io.github.pascalheraud.nativsql.util.StringUtils;

/**
 * Generic repository base class that provides insert and update operations
 * using reflection.
 * Subclasses must implement getTableName() to specify the database table.
 *
 * @param <T>  the entity type
 * @param <ID> the entity identifier type
 */
public abstract class GenericRepository<T extends Entity<ID>, ID> {

    private static final String ID_COLUMN = "id";

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private RowMapperFactory rowMapperFactory;

    @Autowired
    private TypeMapperFactory typeMapperFactory;

    private final Class<T> entityClass;

    @Autowired(required = false)
    protected ApplicationContext applicationContext;

    protected GenericRepository() {
        this.entityClass = getEntityClass();
    }

    abstract protected Class<T> getEntityClass();

    /**
     * Returns the name of the database table.
     */
    @NonNull
    protected abstract String getTableName();

    /**
     * Inserts an entity with specified columns.
     *
     * @param entity  the entity to insert
     * @param columns the property names (camelCase) to insert (must not be empty)
     * @return the number of rows inserted
     * @throws IllegalArgumentException if columns array is empty
     */
    public int insert(T entity, String... columns) {
        String columnList = SqlUtils.getColumnsList(columns);

        Map<String, Object> params = extractValues(entity, columns);
        Map<String, Class<?>> propertyTypes = getPropertyTypes(entity, columns);

        String paramList = Arrays.stream(columns)
                .map(col -> formatParameter(col, propertyTypes.get(col)))
                .collect(Collectors.joining(", "));

        String sql = formatQuery("INSERT INTO %s (%s) VALUES (%s)",
                getTableName(), columnList, paramList);

        return executeUpdate(sql, params);
    }

    @SuppressWarnings("null")
    @NonNull
    private String formatQuery(String sql, Object... params) {
        if (params == null || params.length == 0) {
            throw new IllegalArgumentException("At least one column must be specified");
        }
        return String.format(sql, params);
    }

    /**
     * Executes an UPDATE, INSERT or DELETE SQL statement.
     *
     * @param sql    the SQL statement to execute
     * @param params the query parameters
     * @return the number of rows affected
     */
    protected int executeUpdate(@NonNull String sql, @NonNull Map<String, Object> params) {
        return jdbcTemplate.update(sql, params);
    }

    /**
     * Updates an entity with specified columns (assumes ID column is named "id").
     *
     * @param entity   the entity to update
     * @param columns  the property names (camelCase) to update
     * @return the number of rows updated
     */
    public int update(T entity, String... columns) {
        Map<String, Object> params = extractValues(entity, columns);
        Object id = extractValue(entity, ID_COLUMN);
        params.put(ID_COLUMN, id);

        Map<String, Class<?>> propertyTypes = getPropertyTypes(entity, columns);

        String setClause = Arrays.stream(columns)
                .map(col -> StringUtils.camelToSnake(col) + " = " + formatParameter(col, propertyTypes.get(col)))
                .collect(Collectors.joining(", "));

        String idColumnSnake = StringUtils.camelToSnake(ID_COLUMN);
        String sql = "UPDATE " + getTableName() + " SET " + setClause + " WHERE " + idColumnSnake + " = :" + ID_COLUMN;

        return executeUpdate(sql, params);
    }


    @SuppressWarnings("null")
    @NonNull
    private Map<String, Object> getMap(String idColumn, Object id) {
        return Map.of(idColumn, id);
    }

    /**
     * Deletes an entity by ID (assumes ID column is named "id").
     *
     * @param id the ID value
     * @return the number of rows deleted
     */
    public int deleteById(Object id) {
        String idColumnSnake = StringUtils.camelToSnake(ID_COLUMN);
        String sql = "DELETE FROM " + getTableName() + " WHERE " + idColumnSnake + " = :" + ID_COLUMN;

        return executeUpdate(sql, getMap(ID_COLUMN, id));
    }

    /**
     * Deletes an entity by its ID (assumes ID column is named "id").
     *
     * @param entity the entity to delete
     * @return the number of rows deleted
     */
    public int delete(T entity) {
        Object id = extractValue(entity, ID_COLUMN);
        return deleteById(id);
    }

    /**
     * Finds an entity by ID with specified columns (assumes ID column is named
     * "id").
     *
     * @param id the ID value
     * @param columns the property names (camelCase) to retrieve
     * @return the entity or null if not found
     */
    public T findById(Object id, String... columns) {
        return find(
            newFindQuery()
                .select(columns)
                .whereAndEquals(ID_COLUMN, id)
        );
    }


    /**
     * Finds an entity by a property value with specified columns.
     *
     * @param property the property name (camelCase) to filter by
     * @param value    the value to search for
     * @param columns  the property names (camelCase) to retrieve
     * @return the entity or null if not found
     */
    protected T findByProperty(String property, Object value, String... columns) {
        return findByPropertyExpression(StringUtils.camelToSnake(property), property, value, columns);
    }

    /**
     * Finds all entities by a property value with specified columns.
     *
     * @param property the property name (camelCase) to filter by
     * @param value    the value to search for
     * @param columns  the property names (camelCase) to retrieve
     * @return list of matching entities
     */
    protected List<T> findAllByProperty(String property, Object value, String... columns) {
        return findAllByProperty(property, value, new OrderBy().asc(ID_COLUMN), columns);
    }

    protected List<T> findAllByProperty(String property, Object value, OrderBy orderBy, String... columns) {
        return findAllByPropertyExpression(StringUtils.camelToSnake(property), property, value, orderBy, columns);
    }

    /**
     * Finds all entities by a list of property values (for batch loading with IN clause).
     *
     * @param property the property name (camelCase) to filter by
     * @param values   the list of values to search for (uses IN clause)
     * @param columns  the property names (camelCase) to retrieve
     * @return list of matching entities
     */
    protected List<T> findAllByProperty(String property, List<?> values, String... columns) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        String columnList = SqlUtils.getColumnsList(columns);
        String propertySnake = StringUtils.camelToSnake(property);

        // Create indexed parameter names and placeholders
        List<String> placeholders = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < values.size(); i++) {
            String paramName = property + i;
            placeholders.add(":" + paramName);
            params.put(paramName, values.get(i));
        }

        String sql = "SELECT " + columnList + " FROM " + getTableName() + " WHERE " + propertySnake + " IN (" + String.join(",", placeholders) + ")";

        return jdbcTemplate.query(sql, params, rowMapperFactory.getRowMapper(entityClass));
    }

    /**
     * Finds an entity by a property expression with specified columns.
     * Allows using PostgreSQL expressions like (address).city for composite types.
     *
     * @param propertyExpression the SQL expression to filter by (e.g.,
     *                           "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param columns            the property names (camelCase) to retrieve
     * @return the entity or null if not found
     */
    protected T findByPropertyExpression(String propertyExpression, String paramName, Object value, String... columns) {
        String columnList = SqlUtils.getColumnsList(columns);

        String sql = formatQuery("SELECT %s FROM %s WHERE %s = :%s",
                columnList, getTableName(), propertyExpression, paramName);

        List<T> results = jdbcTemplate.query(sql,
                getMap(paramName, value),
                rowMapperFactory.getRowMapper(entityClass));

        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Finds all entities by a property expression with specified columns.
     * Allows using PostgreSQL expressions like (address).city for composite types.
     *
     * @param propertyExpression the SQL expression to filter by (e.g.,
     *                           "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param columns            the property names (camelCase) to retrieve
     * @return list of matching entities
     */
    protected List<T> findAllByPropertyExpression(String propertyExpression, String paramName, Object value,
            String... columns) {
        return findAll(
            newFindQuery()
                .select(columns)
                .whereExpression(propertyExpression, paramName, value)
        );
    }

    protected List<T> findAllByPropertyExpression(String propertyExpression, String paramName, Object value,
            OrderBy orderBy, String... columns) {
        return findAll(
            newFindQuery()
                .select(columns)
                .whereExpression(propertyExpression, paramName, value)
                .orderBy(orderBy)
        );
    }

    /**
     * Finds all entities with specified columns.
     *
     * @param columns the property names (camelCase) to retrieve
     * @return list of all entities
     */
    public List<T> findAll(String... columns) {
        return findAll(newFindQuery().select(columns));
    }

    /**
     * Finds a single entity using a complex FindQuery builder.
     * Loads associations if specified in the query using batch loading.
     * @param query the FindQuery builder with search criteria
     * @return the first matching entity or null if not found
     */
    protected T find(FindQuery query) {
        // Build SQL query using FindQuery
        String sql = query.buildSql();

        // Get parameters with SQL conversion
        Map<String, Object> params = query.getParameters(this::convertToSqlValue);

        // Execute query
        List<T> results = params.isEmpty()
                ? jdbcTemplate.query(sql, rowMapperFactory.getRowMapper(entityClass))
                : jdbcTemplate.query(sql, params, rowMapperFactory.getRowMapper(entityClass));

        if (results.isEmpty()) {
            return null;
        }

        T entity = results.get(0);

        // Load associations for single entity using batch loading
        if (query.hasAssociations()) {
            List<AssociationConfig> associations = query.getAssociations().stream()
                    .map(assoc -> AssociationConfig.of(assoc.getName(), assoc.getColumnsArray()))
                    .toList();
            loadAssociationsInBatch(List.of(entity), associations);
        }

        return entity;
    }

    /**
     * Finds entities using a complex FindQuery builder.
     * Does NOT load associations to avoid N+1 queries.
     */
    protected List<T> findAll(FindQuery query) {
        // Build SQL query using FindQuery
        String sql = query.buildSql();

        // Get parameters with SQL conversion
        Map<String, Object> params = query.getParameters(this::convertToSqlValue);

        // Execute query (no association loading to avoid N+1)
        return params.isEmpty()
                ? jdbcTemplate.query(sql, rowMapperFactory.getRowMapper(entityClass))
                : jdbcTemplate.query(sql, params, rowMapperFactory.getRowMapper(entityClass));
    }

    /**
     * Finds entities using a complex FindQuery builder and loads associations using batch loading.
     * Uses IN clause to load all associations in a single query per association (no N+1).
     * @param query the FindQuery builder with search criteria
     * @param associations list of associations to load with their column configurations
     * @return list of entities with loaded associations
     */
    protected List<T> findAll(FindQuery query, List<AssociationConfig> associations) {
        // Execute the main query
        List<T> entities = findAll(query);

        if (entities.isEmpty() || associations == null || associations.isEmpty()) {
            return entities;
        }

        // Load associations using batch loading (IN clause)
        loadAssociationsInBatch(entities, associations);

        return entities;
    }

    // ==================== Protected Helper Methods ====================

    /**
     * Creates a new FindQuery builder for this repository's table.
     * @return a new FindQuery instance preconfigured with this table name
     */
    protected FindQuery newFindQuery() {
        return FindQuery.of(getTableName());
    }

    // ==================== Private Helper Methods ====================

    /**
     * Extracts values for specified properties.
     */
    private Map<String, Object> extractValues(T entity, String... properties) {
        Map<String, Object> params = new HashMap<>();

        // Create a set of property names for quick lookup
        Set<String> propertySet = new HashSet<>(Arrays.asList(properties));

        // Get all field accessors and filter by requested properties
        for (FieldAccessor fieldAccessor : ReflectionUtils.getFieldAccessors(entity)) {
            if (propertySet.contains(fieldAccessor.getName())) {
                Object value = fieldAccessor.getValue();
                value = convertToSqlValue(value);
                params.put(fieldAccessor.getName(), value);
            }
        }

        return params;
    }

    /**
     * Gets the types of specified properties before SQL conversion.
     */
    private Map<String, Class<?>> getPropertyTypes(T entity, String... properties) {
        Map<String, Class<?>> types = new HashMap<>();

        // Create a set of property names for quick lookup
        Set<String> propertySet = new HashSet<>(Arrays.asList(properties));

        // Get all field accessors and filter by requested properties
        for (FieldAccessor fieldAccessor : ReflectionUtils.getFieldAccessors(entity)) {
            if (propertySet.contains(fieldAccessor.getName())) {
                Object value = fieldAccessor.getValue();
                if (value != null) {
                    types.put(fieldAccessor.getName(), value.getClass());
                }
            }
        }

        return types;
    }

    /**
     * Formats a parameter with appropriate SQL casting for enums and composite
     * types.
     */
    private String formatParameter(String paramName, Class<?> type) {
        if (type != null && type.isEnum()) {
            // Get the registered PostgreSQL type name from the factory
            String enumTypeName = typeMapperFactory.getEnumPgType(type);
            if (enumTypeName == null) {
                // Fallback: convert class name to snake_case
                enumTypeName = StringUtils.camelToSnake(type.getSimpleName());
            }
            return "(:" + paramName + ")::" + enumTypeName;
        }

        if (type != null && typeMapperFactory.isCompositeType(type)) {
            // Get the registered PostgreSQL composite type name
            String compositeTypeName = typeMapperFactory.getCompositePgType(type);
            return "(:" + paramName + ")::" + compositeTypeName;
        }

        return ":" + paramName;
    }

    /**
     * Extracts a single value.
     */
    private Object extractValue(T entity, String property) {
        return ReflectionUtils.invokeGetter(entity, property);
    }

    /**
     * Converts a Java object to its SQL representation.
     * Handles enums, composite types, JSON types, and value objects.
     */
    private Object convertToSqlValue(Object value) {
        if (value == null) {
            return null;
        }

        Class<?> valueClass = value.getClass();

        // Enums → String
        if (valueClass.isEnum()) {
            return ((Enum<?>) value).name();
        }

        // Composite types → PGobject
        if (typeMapperFactory.isCompositeType(valueClass)) {
            return convertToCompositePGObject(value, valueClass);
        }

        // JSON types → PGobject
        if (typeMapperFactory.isJsonType(valueClass)) {
            return typeMapperFactory.toJsonb(value);
        }

        // Value objects with getValue() method
        try {
            Method getValue = valueClass.getMethod("getValue");
            if (getValue.getReturnType() != void.class) {
                return getValue.invoke(value);
            }
        } catch (NoSuchMethodException e) {
            // No getValue() method, continue to default behavior
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new SQLException("Failed to invoke getValue() on value object: " + valueClass.getSimpleName(), e);
        }

        // Default: return as-is
        return value;
    }

    /**
     * Converts a Java object to PostgreSQL composite type using PGobject.
     * This is safer than manual string construction as PostgreSQL driver handles
     * escaping.
     * Example: Address("123 St", "Paris", "75001", "France") → PGobject with type
     * "address_type"
     */
    private org.postgresql.util.PGobject convertToCompositePGObject(Object value, Class<?> valueClass) {
        try {
            // Get the PostgreSQL type name from the factory
            String pgTypeName = typeMapperFactory.getCompositePgType(valueClass);
            if (pgTypeName == null) {
                throw new SQLException("Composite type not registered: " + valueClass.getSimpleName());
            }

            // Build the composite value string (PostgreSQL record format)
            List<String> fieldValues = new ArrayList<>();
            for (Method method : value.getClass().getMethods()) {
                if (ReflectionUtils.isGetter(method)) {
                    Object fieldValue = method.invoke(value);
                    if (fieldValue == null) {
                        fieldValues.add("NULL");
                    } else {
                        // Use PostgreSQL's quoting function for proper escaping
                        fieldValues.add(quoteCompositeValue(fieldValue.toString()));
                    }
                }
            }

            // Create PGobject with the composite type
            org.postgresql.util.PGobject pgObject = new org.postgresql.util.PGobject();
            pgObject.setType(pgTypeName);
            pgObject.setValue("(" + String.join(",", fieldValues) + ")");

            return pgObject;

        } catch (IllegalAccessException | InvocationTargetException | java.sql.SQLException e) {
            throw new SQLException("Failed to convert to composite type: " + valueClass.getSimpleName(), e);
        }
    }

    /**
     * Quotes a value for use in PostgreSQL composite type.
     * Follows PostgreSQL's composite value quoting rules.
     */
    private String quoteCompositeValue(String value) {
        // If value contains special characters, quote it
        if (value.contains(",") || value.contains("(") || value.contains(")") ||
                value.contains("\"") || value.contains("\\") || value.trim().isEmpty()) {
            // Escape backslashes and quotes, then wrap in quotes
            String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + escaped + "\"";
        }
        return value;
    }

    /**
     * Loads OneToMany associations for an entity.
     * Scans for fields annotated with @OneToMany and populates them.
     *
     * @param entity       the entity to load associations for
     * @param associations list of associations to load with their column
     *                     configurations.
     *                     If null or empty, no associations are loaded.
     */
    protected void loadOneToManyAssociations(Entity<ID> entity, List<AssociationConfig> associations) {
        if (applicationContext == null || associations == null || associations.isEmpty()) {
            return; // Cannot load associations without ApplicationContext or if no associations
                    // specified
        }

        // Build a map for quick lookup
        Map<String, AssociationConfig> associationMap = new HashMap<>();
        for (AssociationConfig config : associations) {
            associationMap.put(config.getAssociationField(), config);
        }

        for (FieldAccessor fieldAccessor : ReflectionUtils.getFieldAccessors(entity)) {
            OneToMany annotation = fieldAccessor.getAnnotation(OneToMany.class);

            if (annotation != null) {
                // Check if this association should be loaded
                AssociationConfig config = associationMap.get(fieldAccessor.getName());
                if (config == null) {
                    continue; // Skip this association
                }

                try {
                    // Get the repository bean and cast to GenericRepository
                    GenericRepository<?, ?> repository = (GenericRepository<?, ?>) applicationContext
                            .getBean(annotation.repository());

                    // Get the foreign key value (entity's ID)
                    ID entityId = entity.getId();

                    // Get columns to load
                    String[] columns = config.getColumns();
                    if (columns == null || columns.length == 0) {
                        // Load all columns if not specified
                        columns = SqlUtils.getEntityColumns(annotation.targetEntity());
                    }

                    // Call findAllByProperty directly on the GenericRepository
                    List<?> associatedEntities = repository.findAllByProperty(
                            annotation.mappedBy(),
                            entityId,
                            columns);

                    // Set the field value using FieldAccessor
                    fieldAccessor.setValue(associatedEntities);

                } catch (SQLException e) {
                    throw e; // Re-throw SQL exceptions as-is
                } catch (Exception e) {
                    throw new SQLException(
                            "Failed to load OneToMany association for field: " + fieldAccessor.getName(), e);
                }
            }
        }
    }

    /**
     * Loads associations for multiple entities using batch loading (IN clause).
     * Loads all associations in a single query per association instead of N queries.
     *
     * @param entities      the list of entities to load associations for
     * @param associations  list of associations to load with their column configurations
     */
    private void loadAssociationsInBatch(List<T> entities, List<AssociationConfig> associations) {
        if (applicationContext == null || entities.isEmpty() || associations.isEmpty()) {
            return;
        }

        for (AssociationConfig config : associations) {
            try {
                // Get the first entity to find the OneToMany annotation
                OneToMany annotation = null;
                FieldAccessor associationField = null;

                for (FieldAccessor fieldAccessor : ReflectionUtils.getFieldAccessors(entities.get(0))) {
                    OneToMany ann = fieldAccessor.getAnnotation(OneToMany.class);
                    if (ann != null && fieldAccessor.getName().equals(config.getAssociationField())) {
                        annotation = ann;
                        associationField = fieldAccessor;
                        break;
                    }
                }

                if (annotation == null) {
                    continue; // Skip if annotation not found
                }

                // Get the repository for the associated entity
                GenericRepository<?, ?> repository = (GenericRepository<?, ?>) applicationContext
                        .getBean(annotation.repository());

                // Extract all parent IDs
                List<ID> parentIds = entities.stream()
                        .map(Entity::getId)
                        .distinct()
                        .toList();

                // Get columns to load
                String[] columns = config.getColumns();
                if (columns == null || columns.length == 0) {
                    columns = SqlUtils.getEntityColumns(annotation.targetEntity());
                }

                // Load all associated entities in a single query using IN clause
                String mappedByField = annotation.mappedBy();
                List<?> allAssociatedEntities = repository.findAllByProperty(
                        mappedByField,
                        parentIds, // Pass list of IDs instead of single ID
                        columns);

                // Group associated entities by parent ID for mapping
                Map<ID, List<Object>> associationsByParentId = new HashMap<>();
                for (Object associated : allAssociatedEntities) {
                    Object parentIdValue = ReflectionUtils.invokeGetter(associated, mappedByField);
                    @SuppressWarnings("unchecked")
                    ID parentId = (ID) parentIdValue;
                    associationsByParentId.computeIfAbsent(parentId, k -> new ArrayList<>()).add(associated);
                }

                // Set associations on each entity
                final FieldAccessor finalAssociationField = associationField;
                for (T entity : entities) {
                    List<Object> associatedList = associationsByParentId.getOrDefault(entity.getId(), new ArrayList<>());
                    finalAssociationField.setValue(associatedList);
                }

            } catch (SQLException e) {
                throw e; // Re-throw SQL exceptions as-is
            } catch (Exception e) {
                throw new SQLException(
                        "Failed to load association batch for: " + config.getAssociationField(), e);
            }
        }
    }

}