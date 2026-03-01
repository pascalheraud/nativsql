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

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.db.SnakeCaseIdentifierConverter;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.mapper.RowMapperFactory;
import ovh.heraud.nativsql.util.Association;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.Fields;
import ovh.heraud.nativsql.util.FindQuery;
import ovh.heraud.nativsql.util.OneToManyAssociation;
import ovh.heraud.nativsql.util.OrderBy;
import ovh.heraud.nativsql.util.ReflectionUtils;
import ovh.heraud.nativsql.util.SqlUtils;
import ovh.heraud.nativsql.util.ReflectionUtils.Getter;

/**
 * Generic repository base class that provides insert and update operations
 * using reflection.
 * Subclasses must implement getTableName() to specify the database table.
 *
 * @param <T>  the entity type implementing IEntity
 * @param <ID> the entity identifier type
 */
public abstract class GenericRepository<T extends IEntity<ID>, ID> {

    private static final String ID_COLUMN = "id";

    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    @NonNull
    private RowMapperFactory rowMapperFactory;

    @Autowired
    @NonNull
    private AnnotationManager annotationManager;

    private DatabaseDialect databaseDialect;

    private final IdentifierConverter identifierConverter = new SnakeCaseIdentifierConverter();

    @NonNull
    private final Class<T> entityClass;

    @Autowired(required = false)
    protected ApplicationContext applicationContext;

    private Fields entityFields;

    protected String tableName;

    protected GenericRepository() {
        this.entityClass = getEntityClass();
        this.entityFields = ReflectionUtils.getFields(entityClass);
    }

    /**
     * Constructor for programmatic instantiation (without Spring).
     * Used by subclasses that need to be instantiated directly.
     *
     * @param entityClass      the entity class
     * @param tableName        the database table name
     * @param rowMapperFactory the row mapper factory
     */
    protected GenericRepository(Class<T> entityClass, String tableName, RowMapperFactory rowMapperFactory,
            AnnotationManager annotationManager) {
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.entityFields = ReflectionUtils.getFields(entityClass);
        this.rowMapperFactory = rowMapperFactory;
        this.annotationManager = annotationManager;
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
     * Uses the tableName field if provided via constructor, otherwise must be
     * overridden by subclasses.
     */
    @NonNull
    public String getTableName() {
        if (tableName != null) {
            return tableName;
        }
        throw new UnsupportedOperationException("getTableName() must be implemented or initialized via constructor");
    }

    /**
     * Returns the entity fields metadata.
     * Used by FindQuery builder.
     */
    public Fields getEntityFields() {
        return entityFields;
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
     * Returns the annotation manager for this repository.
     *
     * @return the annotation manager instance
     */
    @NonNull
    public AnnotationManager getAnnotationManager() {
        return annotationManager;
    }

    /**
     * Inserts an entity with specified columns using getter method references.
     * Converts getter references to column names and delegates to
     * {@link #insert(Object, String...)}.
     *
     * @param entity  the entity to insert (will be modified with generated ID)
     * @param getters the getter method references (e.g., User::getEmail,
     *                User::getId)
     * @throws IllegalArgumentException if getters array is empty
     *
     * @see #insert(Object, String...)
     */
    @SafeVarargs
    public final void insert(T entity, Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        insert(entity, columns);
    }

    /**
     * Inserts an entity with specified columns and populates the generated ID.
     *
     * @param entity  the entity to insert (will be modified with generated ID)
     * @param columns the property names (camelCase) to insert (must not be empty)
     * @throws NativSQLException if columns array is empty
     */
    public void insert(T entity, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
        String columnList = SqlUtils.getColumnsList(identifierConverter, columns);

        Map<String, Object> params = extractValues(entity, columns);

        String paramList = Arrays.stream(columns)
                .map(col -> {
                    FieldAccessor<T> field = entityFields.get(col);
                    return formatParameter(col, field);
                })
                .collect(Collectors.joining(", "));

        String sql = formatQuery("INSERT INTO %s (%s) VALUES (%s)",
                getTableName(), columnList, paramList);

        // Try to retrieve generated ID using GeneratedKeyHolder for better reliability
        ID generatedId = insertWithGeneratedKey(sql, params);

        entity.setId(generatedId);
    }

    /**
     * Inserts data using GeneratedKeyHolder to capture the generated ID.
     * Uses keyHolder.getKey() which is database-agnostic (works with both
     * PostgreSQL returning "id" and MySQL returning "GENERATED_KEY").
     * The raw key value is converted to the ID type using the appropriate
     * TypeMapper.
     *
     * @param sql    the INSERT SQL statement
     * @param params the parameter map
     * @return the generated ID
     * @throws NativSQLException if no generated key was returned
     */
    protected ID insertWithGeneratedKey(@NonNull String sql, @NonNull Map<String, Object> params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource source = new MapSqlParameterSource(params);
        // Specify the 'id' column to avoid returning all columns
        try {
            this.jdbcTemplate.update(sql, source, keyHolder, new String[] { ID_COLUMN });
        } catch (DataAccessException e) {
            throw new NativSQLException("Error executing INSERT into " + getTableName() + ": " + e.getMessage(), e);
        }

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new NativSQLException("No generated key returned after INSERT into " + getTableName()
                    + ". Make sure the table has an auto-generated primary key.");
        }

        ID idValue = getDatabaseDialect().getGeneratedKey(keys, ID_COLUMN);
        if (idValue == null) {
            throw new NativSQLException("No " + ID_COLUMN + " column in generated keys for table " + getTableName());
        }

        FieldAccessor<ID> idField = entityFields.get(ID_COLUMN);
        if (idField != null) {
            ITypeMapper<ID> idMapper = databaseDialect.getMapper(idField,
                    annotationManager);
            if (idMapper != null) {
                return idMapper.fromValue(idValue);
            }
        }
        return idValue;
    }

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
        try {
            return jdbcTemplate.update(sql, params);
        } catch (DataAccessException e) {
            throw new NativSQLException("Error executing update on " + getTableName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Updates an entity with specified columns using getter method references
     * (assumes ID column is named "id").
     * Converts getter references to column names and delegates to
     * {@link #update(Object, String...)}.
     * Validates that exactly one row is updated.
     *
     * @param entity  the entity to update
     * @param getters the getter method references (e.g., User::getEmail,
     *                User::getId)
     * @throws NativSQLException if the update doesn't affect exactly one row
     * @see #update(Object, String...)
     */
    @SafeVarargs
    public final void update(T entity, Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        update(entity, columns);
    }

    /**
     * Updates an entity with specified columns (assumes ID column is named "id").
     * Validates that exactly one row is updated.
     *
     * @param entity  the entity to update
     * @param columns the property names (camelCase) to update (must not be empty)
     * @throws NativSQLException if columns is empty or if the update doesn't affect exactly one row
     */
    public void update(T entity, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
        Map<String, Object> params = extractValues(entity, columns);
        FieldAccessor<ID> idField = entityFields.get(ID_COLUMN);
        Object id = idField != null ? idField.getValue(entity) : null;
        params.put(ID_COLUMN, id);

        String setClause = Arrays.stream(columns)
                .map(col -> {
                    FieldAccessor<ID> field = entityFields.get(col);
                    return identifierConverter.toDB(col) + " = " + formatParameter(col, field);
                })
                .collect(Collectors.joining(", "));

        String idColumnSnake = identifierConverter.toDB(ID_COLUMN);
        String sql = "UPDATE " + getTableName() + " SET " + setClause + " WHERE " + idColumnSnake + " = :" + ID_COLUMN;

        int rowsUpdated = executeUpdate(sql, params);
        if (rowsUpdated != 1) {
            throw new NativSQLException("Update failed: expected to update exactly 1 row but updated " + rowsUpdated);
        }
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
    public void deleteById(ID id) {
        String idColumnSnake = identifierConverter.toDB(ID_COLUMN);
        String sql = "DELETE FROM " + getTableName() + " WHERE " + idColumnSnake + " = :" + ID_COLUMN;

        int rowsDeleted = executeUpdate(sql, getMap(ID_COLUMN, id));
        if (rowsDeleted != 1) {
            throw new NativSQLException("Delete failed: expected to delete exactly 1 row but deleted " + rowsDeleted);
        }
    }

    /**
     * Deletes an entity by its ID (assumes ID column is named "id").
     *
     * @param entity the entity to delete
     */
    public void delete(T entity) {
        FieldAccessor<ID> idField = entityFields.get(ID_COLUMN);
        ID id = idField != null ? idField.getValue(entity) : null;
        deleteById(id);
    }

    /**
     * Finds an entity by ID with specified columns using getter method references
     * (assumes ID column is named "id").
     * Converts getter references to column names and delegates to
     * {@link #findById(Object, String...)}.
     *
     * @param id      the ID value
     * @param getters the getter method references (e.g., User::getEmail,
     *                User::getId)
     * @return the entity or null if not found
     * @see #findById(Object, String...)
     */
    @SafeVarargs
    public final T findById(Object id, Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        return findById(id, columns);
    }

    /**
     * Finds an entity by ID with specified columns (assumes ID column is named
     * "id").
     *
     * @param id      the ID value
     * @param columns the property names (camelCase) to retrieve (must not be empty)
     * @return the entity or null if not found
     * @throws NativSQLException if columns is empty
     */
    public T findById(Object id, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
        return find(
                newFindQuery()
                        .select(columns)
                        .whereAndEquals(ID_COLUMN, id));
    }

    /**
     * Finds all entities by a list of IDs with specified columns using getter
     * method references (assumes ID column is named "id").
     * Converts getter references to column names and delegates to
     * {@link #findAllByIds(List, String...)}.
     *
     * @param ids     the list of ID values
     * @param getters the getter method references (e.g., User::getEmail,
     *                User::getId)
     * @return list of matching entities
     * @see #findAllByIds(List, String...)
     */
    @SafeVarargs
    public final List<T> findAllByIds(List<?> ids, Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        return findAllByIds(ids, columns);
    }

    /**
     * Finds all entities by a list of IDs with specified columns (assumes ID column
     * is named "id").
     *
     * @param ids     the list of ID values
     * @param columns the property names (camelCase) to retrieve (must not be empty)
     * @return list of matching entities
     * @throws NativSQLException if columns is empty
     */
    public List<T> findAllByIds(List<?> ids, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
        return findAll(
                newFindQuery()
                        .select(columns)
                        .whereAndIn(ID_COLUMN, ids));
    }

    /**
     * Finds an entity by a property value with specified columns using getter
     * method references.
     * Converts getter references to column names and delegates to
     * {@link #findByProperty(String, Object, String...)}.
     *
     * @param property the property name (camelCase) to filter by
     * @param value    the value to search for
     * @param getters  the getter method references (e.g., User::getEmail,
     *                 User::getId)
     * @return the entity or null if not found
     * @see #findByProperty(String, Object, String...)
     */
    @SafeVarargs
    protected final T findByProperty(String property, Object value, Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        return findByProperty(property, value, columns);
    }

    /**
     * Finds an entity by a property value with specified columns.
     *
     * @param property the property name (camelCase) to filter by
     * @param value    the value to search for
     * @param columns  the property names (camelCase) to retrieve (must not be empty)
     * @return the entity or null if not found
     * @throws NativSQLException if columns is empty
     */
    protected T findByProperty(String property, Object value, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
        return findByPropertyExpression(identifierConverter.toDB(property), property, value,
                columns);
    }

    /**
     * Finds all entities by a property value with specified columns using getter
     * method references.
     * Converts getter references to column names and delegates to
     * {@link #findAllByProperty(String, Object, String...)}.
     *
     * @param property the property name (camelCase) to filter by
     * @param value    the value to search for
     * @param getters  the getter method references (e.g., User::getEmail,
     *                 User::getId)
     * @return list of matching entities
     * @see #findAllByProperty(String, Object, String...)
     */
    @SafeVarargs
    protected final List<T> findAllByProperty(String property, Object value, Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        return findAllByProperty(property, value, columns);
    }

    /**
     * Finds all entities by a property value with specified columns.
     *
     * @param property the property name (camelCase) to filter by
     * @param value    the value to search for
     * @param columns  the property names (camelCase) to retrieve (must not be empty)
     * @return list of matching entities
     * @throws NativSQLException if columns is empty
     */
    protected List<T> findAllByProperty(String property, Object value, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
        return findAllByProperty(property, value, new OrderBy().asc(ID_COLUMN), columns);
    }

    /**
     * Finds all entities by a property value with specified columns and order using
     * getter method references.
     * Converts getter references to column names and delegates to
     * {@link #findAllByProperty(String, Object, OrderBy, String...)}.
     *
     * @param property the property name (camelCase) to filter by
     * @param value    the value to search for
     * @param orderBy  the order by clause
     * @param getters  the getter method references (e.g., User::getEmail,
     *                 User::getId)
     * @return list of matching entities
     * @see #findAllByProperty(String, Object, OrderBy, String...)
     */
    @SafeVarargs
    protected final List<T> findAllByProperty(String property, Object value, OrderBy orderBy, Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        return findAllByProperty(property, value, orderBy, columns);
    }

    protected List<T> findAllByProperty(String property, Object value, OrderBy orderBy, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
        return findAllByPropertyExpression(identifierConverter.toDB(property), property, value,
                orderBy, columns);
    }

    /**
     * Finds all entities by a list of property values using getter method references (for batch loading with IN clause).
     * Converts getter references to column names and delegates to {@link #findAllByProperty(String, List, String...)}.
     *
     * @param property the property name (camelCase) to filter by
     * @param values   the list of values to search for (uses IN clause)
     * @param getters  the getter method references (e.g., User::getEmail, User::getId)
     * @return list of matching entities
     * @see #findAllByProperty(String, List, String...)
     */
    @SafeVarargs
    protected final List<T> findAllByProperty(String property, List<?> values, Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        return findAllByProperty(property, values, columns);
    }

    /**
     * Finds all entities by a list of property values using IN clause (batch loading).
     *
     * @param property the property name (camelCase) to filter by
     * @param values   the list of values to search for (uses IN clause)
     * @param columns  the property names (camelCase) to retrieve (must not be empty)
     * @return list of matching entities
     * @throws NativSQLException if columns is empty
     */
    protected List<T> findAllByProperty(String property, List<?> values, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
        return findAllByPropertyIn(property, values, columns);
    }

    /**
     * Finds all entities by a list of property values using IN clause with specified columns using getter method references.
     * More explicit variant of findAllByProperty for batch loading scenarios.
     * Converts getter references to column names and delegates to {@link #findAllByPropertyIn(String, List, String...)}.
     *
     * @param property the property name (camelCase) to filter by
     * @param values   the list of values to search for (uses IN clause)
     * @param getters  the getter method references (e.g., User::getEmail, User::getId)
     * @return list of matching entities
     * @see #findAllByPropertyIn(String, List, String...)
     */
    @SafeVarargs
    protected final List<T> findAllByPropertyIn(String property, List<?> values, Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        return findAllByPropertyIn(property, values, columns);
    }

    /**
     * Finds all entities by a list of property values using IN clause.
     * More explicit variant of findAllByProperty for batch loading scenarios.
     *
     * @param property the property name (camelCase) to filter by
     * @param values   the list of values to search for (uses IN clause)
     * @param columns  the property names (camelCase) to retrieve (must not be empty)
     * @return list of matching entities
     * @throws NativSQLException if columns is empty
     */
    protected List<T> findAllByPropertyIn(String property, List<?> values, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
        return findAll(newFindQuery()
                .select(columns)
                .whereAndIn(property, values));
    }

    /**
     * Finds an entity by a property expression with specified columns using getter method references.
     * Allows using database expressions like (address).city for composite types.
     * Converts getter references to column names and delegates to {@link #findByPropertyExpression(String, String, Object, String...)}.
     *
     * @param propertyExpression the SQL expression to filter by (e.g., "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param getters            the getter method references (e.g., User::getEmail, User::getId)
     * @return the entity or null if not found
     * @see #findByPropertyExpression(String, String, Object, String...)
     */
    @SafeVarargs
    protected final T findByPropertyExpression(String propertyExpression, String paramName, Object value, Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        return findByPropertyExpression(propertyExpression, paramName, value, columns);
    }

    /**
     * Finds an entity by a property expression with specified columns.
     * Allows using database expressions like (address).city for composite types.
     *
     * @param propertyExpression the SQL expression to filter by (e.g.,
     *                           "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param columns            the property names (camelCase) to retrieve (must not be empty)
     * @return the entity or null if not found
     * @throws NativSQLException if columns is empty
     */
    protected T findByPropertyExpression(String propertyExpression, String paramName, Object value, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
        return find(newFindQuery().select(columns).whereExpression(propertyExpression, paramName, value));
    }

    /**
     * Finds all entities by a property expression with specified columns using getter method references.
     * Allows using database expressions like (address).city for composite types.
     * Converts getter references to column names and delegates to {@link #findAllByPropertyExpression(String, String, Object, String...)}.
     *
     * @param propertyExpression the SQL expression to filter by (e.g., "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param getters            the getter method references (e.g., User::getEmail, User::getId)
     * @return list of matching entities
     * @see #findAllByPropertyExpression(String, String, Object, String...)
     */
    @SafeVarargs
    protected final List<T> findAllByPropertyExpression(String propertyExpression, String paramName, Object value,
            Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        return findAllByPropertyExpression(propertyExpression, paramName, value, columns);
    }

    /**
     * Finds all entities by a property expression with specified columns.
     * Allows using database expressions like (address).city for composite types.
     *
     * @param propertyExpression the SQL expression to filter by (e.g.,
     *                           "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param columns            the property names (camelCase) to retrieve (must not be empty)
     * @return list of matching entities
     * @throws NativSQLException if columns is empty
     */
    protected List<T> findAllByPropertyExpression(String propertyExpression, String paramName, Object value,
            String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
        return findAll(
                newFindQuery()
                        .select(columns)
                        .whereExpression(propertyExpression, paramName, value));
    }

    /**
     * Finds all entities by a property expression with specified columns and order using getter method references.
     * Allows using database expressions like (address).city for composite types.
     * Converts getter references to column names and delegates to {@link #findAllByPropertyExpression(String, String, Object, OrderBy, String...)}.
     *
     * @param propertyExpression the SQL expression to filter by (e.g., "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param orderBy            the order by clause
     * @param getters            the getter method references (e.g., User::getEmail, User::getId)
     * @return list of matching entities
     * @see #findAllByPropertyExpression(String, String, Object, OrderBy, String...)
     */
    @SafeVarargs
    protected final List<T> findAllByPropertyExpression(String propertyExpression, String paramName, Object value,
            OrderBy orderBy, Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        return findAllByPropertyExpression(propertyExpression, paramName, value, orderBy, columns);
    }

    /**
     * Finds all entities by a property expression with specified columns and order.
     * Allows using database expressions like (address).city for composite types.
     *
     * @param propertyExpression the SQL expression to filter by (e.g., "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param orderBy            the order by clause
     * @param columns            the property names (camelCase) to retrieve (must not be empty)
     * @return list of matching entities
     * @throws NativSQLException if columns is empty
     */
    protected List<T> findAllByPropertyExpression(String propertyExpression, String paramName, Object value,
            OrderBy orderBy, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
        return findAll(
                newFindQuery()
                        .select(columns)
                        .whereExpression(propertyExpression, paramName, value)
                        .orderBy(orderBy));
    }

    /**
     * Finds all entities with specified columns using getter method references.
     * Converts getter references to column names and delegates to {@link #findAll(String...)}.
     *
     * @param getters the getter method references (e.g., User::getEmail, User::getId)
     * @return list of all entities
     * @see #findAll(String...)
     */
    @SafeVarargs
    public final List<T> findAll(Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        return findAll(columns);
    }

    /**
     * Finds all entities with specified columns.
     *
     * @param columns the property names (camelCase) to retrieve (must not be empty)
     * @return list of all entities
     * @throws NativSQLException if columns is empty
     */
    public List<T> findAll(String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }
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
        Map<String, Object> params = query.getParameters();

        // Execute query with joins for nested object mapping
        List<T> results = findAllExternal(sql, params, entityClass);
        T entity = getFirstOrNull(results);

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
        Map<String, Object> params = query.getParameters();

        // Execute query with joins for nested object mapping (no association loading to
        // avoid N+1)
        return findAllExternal(sql, params, entityClass);
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
        for (FieldAccessor<?> fieldAccessor : entityFields.list()) {
            if (propertySet.contains(fieldAccessor.getName())) {
                Object value = fieldAccessor.getValue(entity);

                // Get the declared DbDataType from @Type annotation, or null as default
                DbDataType dbDataType = null;
                var typeInfo = annotationManager.getTypeInfo(fieldAccessor);
                if (typeInfo != null && typeInfo.getDataType() != null) {
                    dbDataType = typeInfo.getDataType();
                }

                value = convertToSqlValue(value, fieldAccessor, dbDataType);
                params.put(fieldAccessor.getName(), value);
            }
        }

        return params;
    }

    /**
     * Gets the types of specified properties before SQL conversion.
     */
    /**
     * Formats a parameter with appropriate SQL casting for enums and composite
     * types using the TypeMapper.
     */
    private <PARAM_T> String formatParameter(String paramName, FieldAccessor<PARAM_T> fieldAccessor) {
        return databaseDialect.getMapper(fieldAccessor, annotationManager).formatParameter(paramName);
    }

    /**
     * Converts a Java object to its SQL representation based on the declared
     * DbDataType.
     * Handles enums, composite types, JSON types, and value objects.
     * For lists, converts each element in the list using the element's type mapper.
     * Returns null if value is null.
     * If dataType is null, the mapper will use its default behavior.
     */
    private <PARAM_T> Object convertToSqlValue(PARAM_T value, FieldAccessor<?> fieldAccessor, DbDataType dataType) {
        if (value == null) {
            return null;
        }

        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> {
                        if (item == null) {
                            return null;
                        }
                        @SuppressWarnings("unchecked")
                        ITypeMapper<Object> itemMapper = (ITypeMapper<Object>) databaseDialect
                                .getMapper(fieldAccessor, annotationManager);
                        if (itemMapper == null) {
                            throw new IllegalArgumentException(
                                    "No TypeMapper found for type: " + item.getClass().getName() +
                                            ". Please ensure the type is properly configured in the database dialect.");
                        }
                        return itemMapper.toDatabase(item, dataType);
                    })
                    .collect(Collectors.toList());
        }

        // Get the mapper for the actual value type
        @SuppressWarnings("unchecked")
        ITypeMapper<PARAM_T> mapper = (ITypeMapper<PARAM_T>) databaseDialect.getMapper(fieldAccessor,
                annotationManager);
        if (mapper == null) {
            throw new IllegalArgumentException(
                    "No TypeMapper found for type: " + value.getClass().getName() +
                            ". Please ensure the type is properly configured in the database dialect.");
        }
        // Use toDatabase with the declared dataType (or null if no specific type
        // declared)
        return mapper.toDatabase(value, dataType);
    }

    /**
     * Converts all parameters in a map to their SQL representations using type
     * mappers. Checks for @Type annotations on entity fields to determine the
     * appropriate database type for conversion.
     */
    private Map<String, Object> convertParamsToSqlValues(Map<String, Object> params) {
        Map<String, Object> converted = new HashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            DbDataType dbDataType = null;

            // Try to find the field in the entity and get its declared DbDataType
            FieldAccessor<Object> field = entityFields.get(entry.getKey());
            if (field != null) {
                var typeInfo = annotationManager.getTypeInfo(field);
                if (typeInfo != null && typeInfo.getDataType() != null) {
                    dbDataType = typeInfo.getDataType();
                }
            } else if (entry.getValue() != null) {
                field = new FieldAccessor<Object>(entry.getValue().getClass());
            }

            converted.put(entry.getKey(), convertToSqlValue(entry.getValue(), field, dbDataType));
        }
        return converted;
    }

    /**
     * Returns the first element of a list or null if empty.
     * Validates that the list contains at most one element.
     *
     * @param results the list to get the first element from
     * @return the first element or null if empty
     * @throws NativSQLException if the list contains more than one element
     */
    private <E> E getFirstOrNull(List<E> results) {
        if (results.size() > 1) {
            throw new NativSQLException("Query returned multiple results but expected at most one");
        }
        return results.isEmpty() ? null : results.get(0);
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
    protected void loadOneToManyAssociations(IEntity<ID> entity, List<Association> associations) {
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
    private void loadOneToManyAssociation(IEntity<ID> entity, Association config) {
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
    private <SUBT extends IEntity<ID>> void loadAssociationInBatch(List<T> entities, Association association) {
        FieldAccessor<List<SUBT>> fieldAccessor = entityFields.get(association.getName());
        if (fieldAccessor == null) {
            throw new NativSQLException("Association field not found: " + association.getName());
        }

        OneToManyAssociation associationAnnotation = annotationManager.getOneToManyInfo(fieldAccessor);
        if (associationAnnotation == null) {
            throw new NativSQLException("Field is not annotated with @OneToMany: " + association.getName());
        }

        // Get the repository for the associated entity
        @SuppressWarnings("unchecked")
        GenericRepository<SUBT, ID> repository = (GenericRepository<SUBT, ID>) applicationContext
                .getBean(associationAnnotation.getRepositoryClass());

        // Create a map of entities by their ID for direct access
        Map<ID, T> entitiesById = entities.stream()
                .collect(Collectors.toMap(IEntity::getId, e -> e));

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
        @SuppressWarnings("unchecked")
        FieldAccessor<V> accessor = (FieldAccessor<V>) entityFields.get(fieldName);
        if (accessor == null) {
            throw new NativSQLException("Field not found: " + fieldName);
        }
        return accessor.getValue(entity);
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
        List<EXT> results = findAllExternal(sql, params, resultClass);
        return getFirstOrNull(results);
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
        Map<String, Object> convertedParams = convertParamsToSqlValues(params);
        return jdbcTemplate.query(sql, convertedParams,
                rowMapperFactory.getRowMapper(resultClass, databaseDialect, identifierConverter));
    }

}