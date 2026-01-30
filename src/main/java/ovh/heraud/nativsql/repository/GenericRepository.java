package ovh.heraud.nativsql.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.annotation.PostConstruct;

import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.db.SnakeCaseIdentifierConverter;
import ovh.heraud.nativsql.domain.Entity;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.mapper.RowMapperFactory;
import ovh.heraud.nativsql.util.Association;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.Fields;
import ovh.heraud.nativsql.util.FindQuery;
import ovh.heraud.nativsql.util.Join;
import ovh.heraud.nativsql.util.OneToManyAssociation;
import ovh.heraud.nativsql.util.OrderBy;
import ovh.heraud.nativsql.util.ReflectionUtils;
import ovh.heraud.nativsql.util.SqlUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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

    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    @NonNull
    private RowMapperFactory rowMapperFactory;

    private DatabaseDialect databaseDialect;

    private final IdentifierConverter identifierConverter = new SnakeCaseIdentifierConverter();

    @NonNull
    private final Class<T> entityClass;

    @Autowired(required = false)
    protected ApplicationContext applicationContext;

    private Fields entityFields;

    protected GenericRepository() {
        this.entityClass = getEntityClass();
        this.entityFields = ReflectionUtils.getFields(entityClass);
    }

    @PostConstruct
    protected void initJdbcTemplate() {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(getDataSource());
        this.databaseDialect = getDatabaseDialectInstance();
    }

    @NonNull
    protected abstract DataSource getDataSource();

    @NonNull
    abstract protected Class<T> getEntityClass();

    protected abstract DatabaseDialect getDatabaseDialectInstance();

    /**
     * Returns the name of the database table.
     */
    @NonNull /*  */
    protected abstract String getTableName();

    /**
     * Returns the entity fields metadata.
     * Used by FindQuery builder.
     */
    public Fields getEntityFields() {
        return entityFields;
    }

    /**
     * Returns the name of the database table.
     * Used by FindQuery builder.
     */
    public String getTableNameForQuery() {
        return getTableName();
    }

    /**
     * Returns the database dialect for this repository.
     *
     * @return the database dialect instance
     */
    public DatabaseDialect getDatabaseDialect() {
        return databaseDialect;
    }

    /**
     * Returns the identifier converter for this repository.
     *
     * @return the identifier converter instance
     */
    public IdentifierConverter getIdentifierConverter() {
        return identifierConverter;
    }

    /**
     * Inserts an entity with specified columns and populates the generated ID.
     *
     * @param entity  the entity to insert (will be modified with generated ID)
     * @param columns the property names (camelCase) to insert (must not be empty)
     * @throws IllegalArgumentException if columns array is empty
     */
    public void insert(T entity, String... columns) {
        String columnList = SqlUtils.getColumnsList(identifierConverter, columns);

        Map<String, Object> params = extractValues(entity, columns);
        Map<String, Class<?>> propertyTypes = getPropertyTypes(entity, columns);

        String paramList = Arrays.stream(columns)
                .map(col -> formatParameter(col, propertyTypes.get(col)))
                .collect(Collectors.joining(", "));

        String sql = formatQuery("INSERT INTO %s (%s) VALUES (%s)",
                getTableName(), columnList, paramList);

        executeUpdate(sql, params);

        // Retrieve and set the generated ID on the entity
        Long generatedId = getLastInsertedId();
        if (generatedId != null) {
            FieldAccessor idField = entityFields.get(ID_COLUMN);
            if (idField != null) {
                @SuppressWarnings("unchecked")
                ID id = (ID) generatedId;
                idField.setValue(entity, id);
            }
        }
    }

    /**
     * Retrieves the last inserted ID from the database.
     * This method must be implemented by database-specific repositories.
     *
     * @return the last inserted ID
     */
    protected abstract Long getLastInsertedId();

    @NonNull
    private String formatQuery(String sql, Object... params) {
        if (params == null || params.length == 0) {
            throw new IllegalArgumentException("At least one column must be specified");
        }
        return Objects.requireNonNull(String.format(sql, params));
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
     * @param entity  the entity to update
     * @param columns the property names (camelCase) to update
     * @return the number of rows updated
     */
    public int update(T entity, String... columns) {
        Map<String, Object> params = extractValues(entity, columns);
        FieldAccessor idField = entityFields.get(ID_COLUMN);
        Object id = idField != null ? idField.getValue(entity) : null;
        params.put(ID_COLUMN, id);

        Map<String, Class<?>> propertyTypes = getPropertyTypes(entity, columns);

        String setClause = Arrays.stream(columns)
                .map(col -> identifierConverter.toDB(col) + " = "
                        + formatParameter(col, propertyTypes.get(col)))
                .collect(Collectors.joining(", "));

        String idColumnSnake = identifierConverter.toDB(ID_COLUMN);
        String sql = "UPDATE " + getTableName() + " SET " + setClause + " WHERE " + idColumnSnake + " = :" + ID_COLUMN;

        return executeUpdate(sql, params);
    }

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
        String idColumnSnake = identifierConverter.toDB(ID_COLUMN);
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
        FieldAccessor idField = entityFields.get(ID_COLUMN);
        Object id = idField != null ? idField.getValue(entity) : null;
        return deleteById(id);
    }

    /**
     * Finds an entity by ID with specified columns (assumes ID column is named
     * "id").
     *
     * @param id      the ID value
     * @param columns the property names (camelCase) to retrieve
     * @return the entity or null if not found
     */
    public T findById(Object id, String... columns) {
        return find(
                newFindQuery()
                        .select(columns)
                        .whereAndEquals(ID_COLUMN, id));
    }

    /**
     * Finds all entities by a list of IDs with specified columns (assumes ID column is named "id").
     *
     * @param ids     the list of ID values
     * @param columns the property names (camelCase) to retrieve
     * @return list of matching entities
     */
    public List<T> findAllByIds(List<?> ids, String... columns) {
        return findAll(
                newFindQuery()
                        .select(columns)
                        .whereAndIn(ID_COLUMN, ids));
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
        return findByPropertyExpression(identifierConverter.toDB(property), property, value,
                columns);
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
        return findAllByPropertyExpression(identifierConverter.toDB(property), property, value,
                orderBy, columns);
    }

    /**
     * Finds all entities by a list of property values (for batch loading with IN
     * clause).
     *
     * @param property the property name (camelCase) to filter by
     * @param values   the list of values to search for (uses IN clause)
     * @param columns  the property names (camelCase) to retrieve
     * @return list of matching entities
     */
    /**
     * Finds all entities by a list of property values using IN clause (batch loading).
     *
     * @param property the property name (camelCase) to filter by
     * @param values   the list of values to search for (uses IN clause)
     * @param columns  the property names (camelCase) to retrieve
     * @return list of matching entities
     */
    protected List<T> findAllByProperty(String property, List<?> values, String... columns) {
        return findAllByPropertyIn(property, values, columns);
    }

    /**
     * Finds all entities by a list of property values using IN clause.
     * More explicit variant of findAllByProperty for batch loading scenarios.
     *
     * @param property the property name (camelCase) to filter by
     * @param values   the list of values to search for (uses IN clause)
     * @param columns  the property names (camelCase) to retrieve
     * @return list of matching entities
     */
    protected List<T> findAllByPropertyIn(String property, List<?> values, String... columns) {
        return findAll(newFindQuery()
                .select(columns)
                .whereAndIn(property, values));
    }

    /**
     * Finds an entity by a property expression with specified columns.
     * Allows using database expressions like (address).city for composite types.
     *
     * @param propertyExpression the SQL expression to filter by (e.g.,
     *                           "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param columns            the property names (camelCase) to retrieve
     * @return the entity or null if not found
     */
    protected T findByPropertyExpression(String propertyExpression, String paramName, Object value, String... columns) {
        return find(newFindQuery().select(columns).whereExpression(propertyExpression, paramName, value));
    }

    /**
     * Finds all entities by a property expression with specified columns.
     * Allows using database expressions like (address).city for composite types.
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
                        .whereExpression(propertyExpression, paramName, value));
    }

    protected List<T> findAllByPropertyExpression(String propertyExpression, String paramName, Object value,
            OrderBy orderBy, String... columns) {
        return findAll(
                newFindQuery()
                        .select(columns)
                        .whereExpression(propertyExpression, paramName, value)
                        .orderBy(orderBy));
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
     * If JOINs are specified, uses them for nested object mapping.
     *
     * @param query the FindQuery builder with search criteria
     * @return the first matching entity or null if not found
     */
    protected T find(FindQuery<T, ID> query) {
        // Build SQL query using FindQuery
        String sql = query.buildString(identifierConverter);

        // Get parameters with SQL conversion
        Map<String, Object> params = query.getParameters(this::convertToSqlValue);

        // Execute query with joins for nested object mapping
        T entity = findExternal(sql, params, entityClass, query.getJoins());

        // Load associations for multiple linked entities using batch loading
        if (entity != null && query.hasAssociations()) {
            loadAssociationsInBatch(List.of(entity), query.getAssociations());
        }

        return entity;
    }

    /**
     * Finds entities using a complex FindQuery builder.
     * Does NOT load associations to avoid N+1 queries.
     */
    protected List<T> findAll(FindQuery<T, ID> query) {
        // Build SQL query using FindQuery
        String sql = query.buildString(identifierConverter);

        // Get parameters with SQL conversion
        Map<String, Object> params = query.getParameters(this::convertToSqlValue);

        // Execute query with joins for nested object mapping (no association loading to
        // avoid N+1)
        List<T> entities = findAllExternal(sql, params, entityClass, query.getJoins());
        return entities;
    }

    // ==================== Protected Helper Methods ====================

    /**
     * Creates a new FindQuery builder for this repository's table.
     *
     * @return a new FindQuery instance preconfigured with this repository
     */
    protected FindQuery<T, ID> newFindQuery() {
        return FindQuery.of(this);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Extracts values for specified properties.
     */
    @NonNull
    private Map<String, Object> extractValues(T entity, String... properties) {
        Map<String, Object> params = new HashMap<>();

        // Create a set of property names for quick lookup
        Set<String> propertySet = new HashSet<>(Arrays.asList(properties));

        // Get all field accessors and filter by requested properties
        for (FieldAccessor fieldAccessor : entityFields.list()) {
            if (propertySet.contains(fieldAccessor.getName())) {
                Object value = fieldAccessor.getValue(entity);
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
        for (FieldAccessor fieldAccessor : entityFields.list()) {
            if (propertySet.contains(fieldAccessor.getName())) {
                Object value = fieldAccessor.getValue(entity);
                if (value != null) {
                    types.put(fieldAccessor.getName(), value.getClass());
                }
            }
        }

        return types;
    }

    /**
     * Formats a parameter with appropriate SQL casting for enums and composite
     * types using the TypeMapper.
     */
    private <PARAM_T> String formatParameter(String paramName, Class<PARAM_T> type) {
        return databaseDialect.getMapper(type).formatParameter(paramName);
    }

    /**
     * Converts a Java object to its SQL representation.
     * Handles enums, composite types, JSON types, and value objects.
     * For lists, converts each element in the list using the element's type mapper.
     */
    @SuppressWarnings("unchecked")
    private <PARAM_T> Object convertToSqlValue(PARAM_T value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> {
                        if (item == null) {
                            return null;
                        }
                        ITypeMapper<Object> itemMapper = (ITypeMapper<Object>) databaseDialect.getMapper(item.getClass());
                        return itemMapper.toDatabase(item);
                    })
                    .collect(Collectors.toList());
        }
        ITypeMapper<PARAM_T> mapper = (ITypeMapper<PARAM_T>) databaseDialect.getMapper(value.getClass());
        return mapper.toDatabase(value);
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
    protected void loadOneToManyAssociations(Entity<ID> entity, List<Association> associations) {
        if (applicationContext == null || associations == null || associations.isEmpty()) {
            return; // Cannot load associations without ApplicationContext or if no associations
                    // specified
        }

        for (Association config : associations) {
            loadOneToManyAssociation(entity, config);
        }
    }

    /**
     * Loads a single OneToMany association for an entity.
     *
     * @param entity the entity to load the association for
     * @param config the association configuration
     */
    private void loadOneToManyAssociation(Entity<ID> entity, Association config) {
        @SuppressWarnings("unchecked")
        List<T> entities = (List<T>) (List<?>) List.of(entity);
        loadAssociationInBatch(entities, config);
    }

    /**
     * Loads associations for multiple entities using batch loading (IN clause).
     * Loads all associations in a single query per association instead of N
     * queries.
     *
     * @param entities     the list of entities to load associations for
     * @param associations list of associations to load with their column
     *                     configurations
     */
    private void loadAssociationsInBatch(List<T> entities, List<Association> associations) {
        if (applicationContext == null || entities.isEmpty() || associations.isEmpty()) {
            return;
        }

        for (Association config : associations) {
            loadAssociationInBatch(entities, config);
        }
    }

    /**
     * Loads a single association for multiple entities using batch loading.
     *
     * @param entities    the list of entities to load the association for
     * @param association the association configuration
     */
    private <SUBT extends Entity<ID>> void loadAssociationInBatch(List<T> entities, Association association) {
        FieldAccessor fieldAccessor = entityFields.get(association.getName());
        if (fieldAccessor == null) {
            throw new NativSQLException("Association field not found: " + association.getName());
        }

        OneToManyAssociation associationAnnotation = fieldAccessor.getOneToMany();

        // Get the repository for the associated entity
        @SuppressWarnings("unchecked")
        GenericRepository<SUBT, ID> repository = (GenericRepository<SUBT, ID>) applicationContext
                .getBean(associationAnnotation.getRepositoryClass());

        // Create a map of entities by their ID for direct access
        Map<ID, T> entitiesById = entities.stream()
                .collect(Collectors.toMap(Entity::getId, e -> e));

        // Get columns to load from association configuration
        List<String> columns = new ArrayList<>(association.getColumns());
        if (columns.isEmpty()) {
            throw new NativSQLException(
                    "Association configuration must specify columns for: " + association.getName());
        }

        // Ensure foreign key field is included in columns for proper grouping
        String foreignKeyField = associationAnnotation.getForeignKey();
        if (!columns.contains(foreignKeyField)) {
            columns.add(foreignKeyField);
        }

        List<ID> foreignKeyValues = entitiesById.keySet().stream().distinct().toList();
        List<SUBT> allAssociatedEntities = repository.findAllByPropertyIn(foreignKeyField, foreignKeyValues,
                columns.toArray(new String[0]));

        // Initialize associations on each entity
        for (T entity : entities) {
            fieldAccessor.setValue(entity, new ArrayList<>());
        }

        // Add associated entities directly to their parent
        for (SUBT associated : allAssociatedEntities) {
            ID parentIdValue = repository.getFieldValue(associated, foreignKeyField);
            T parentEntity = entitiesById.get(parentIdValue);
            if (parentEntity != null) {
                List<SUBT> associatedList = fieldAccessor.getValue(parentEntity);
                associatedList.add(associated);
            }
        }
    }

    /**
     * Gets a field value from an entity using the repository's cached Fields.
     *
     * @param entity    the entity to get the value from
     * @param fieldName the field name
     * @return the field value with the correct type
     * @throws NativSQLException if the field is not found
     */
    protected <V> V getFieldValue(Object entity, String fieldName) throws NativSQLException {
        FieldAccessor accessor = entityFields.get(fieldName);
        if (accessor == null) {
            throw new NativSQLException("Field not found: " + fieldName);
        }
        @SuppressWarnings("unchecked")
        V value = (V) accessor.getValue(entity);
        return value;
    }

    /**
     * Executes a custom SQL query and returns a single external object with joins
     * for nested mapping.
     * Useful for queries with JOINs that need to map nested objects.
     *
     * @param <EXT>       the type of the external object to return
     * @param sql         the SQL query to execute
     * @param params      the query parameters
     * @param resultClass the class of the external object to return
     * @param joins       the list of joins for nested object mapping
     * @return the first result or null if not found
     */
    protected <EXT> EXT findExternal( String sql,  Map<String, Object> params,
             Class<EXT> resultClass,  List<Join> joins) {
        List<EXT> results = jdbcTemplate.query(sql, params,
                rowMapperFactory.getRowMapper(resultClass, databaseDialect, joins, identifierConverter));
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Executes a custom SQL query and returns a single external object.
     * Useful for reporting, aggregations, and queries returning objects different
     * from the entity type.
     *
     * @param <EXT>       the type of the external object to return
     * @param sql         the SQL query to execute
     * @param resultClass the class of the external object to return
     * @return the first result or null if not found
     */
    protected <EXT> EXT findExternal(@NonNull String sql, @NonNull Class<EXT> resultClass) {
        return findExternal(sql, new HashMap<>(), resultClass);
    }

    /**
     * Executes a custom SQL query and returns a single external object.
     * Useful for reporting, aggregations, and queries returning objects different
     * from the entity type.
     *
     * @param <EXT>       the type of the external object to return
     * @param sql         the SQL query to execute
     * @param params      the query parameters
     * @param resultClass the class of the external object to return
     * @return the first result or null if not found
     */
    protected <EXT> EXT findExternal(@NonNull String sql, @NonNull Map<String, Object> params,
            @NonNull Class<EXT> resultClass) {
        List<EXT> results = jdbcTemplate.query(sql, params,
                rowMapperFactory.getRowMapper(resultClass, databaseDialect, identifierConverter));
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Executes a custom SQL query and returns a list of external objects with joins
     * for nested mapping.
     * Useful for queries with JOINs that need to map nested objects.
     *
     * @param <EXT>       the type of the external objects to return
     * @param sql         the SQL query to execute
     * @param params      the query parameters
     * @param resultClass the class of the external objects to return
     * @param joins       the list of joins for nested object mapping
     * @return a list of results
     */
    @NonNull
    protected <EXT> List<EXT> findAllExternal(@NonNull String sql, @NonNull Map<String, Object> params,
            @NonNull Class<EXT> resultClass, @NonNull List<Join> joins) {
        return jdbcTemplate.query(sql, params,
                rowMapperFactory.getRowMapper(resultClass, databaseDialect, joins, identifierConverter));
    }

    /**
     * Executes a custom SQL query and returns a list of external objects.
     * Useful for reporting, aggregations, and queries returning objects different
     * from the entity type.
     *
     * @param <EXT>       the type of the external objects to return
     * @param sql         the SQL query to execute
     * @param resultClass the class of the external objects to return
     * @return a list of results
     */
    protected <EXT> List<EXT> findAllExternal(@NonNull String sql, @NonNull Class<EXT> resultClass) {
        return findAllExternal(sql, new HashMap<>(), resultClass);
    }

    /**
     * Executes a custom SQL query and returns a list of external objects.
     * Useful for reporting, aggregations, and queries returning objects different
     * from the entity type.
     *
     * @param <EXT>       the type of the external objects to return
     * @param sql         the SQL query to execute
     * @param params      the query parameters
     * @param resultClass the class of the external objects to return
     * @return a list of results
     */
    protected <EXT> List<EXT> findAllExternal(@NonNull String sql, @NonNull Map<String, Object> params,
            @NonNull Class<EXT> resultClass) {
        return jdbcTemplate.query(sql, params,
                rowMapperFactory.getRowMapper(resultClass, databaseDialect, identifierConverter));
    }

}