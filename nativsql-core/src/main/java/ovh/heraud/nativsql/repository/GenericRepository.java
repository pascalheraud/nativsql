package ovh.heraud.nativsql.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import jakarta.annotation.PostConstruct;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.db.SnakeCaseIdentifierConverter;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.mapper.RowMapperFactory;
import ovh.heraud.nativsql.util.Association;
import ovh.heraud.nativsql.util.CountQuery;
import ovh.heraud.nativsql.util.ExistsQuery;
import ovh.heraud.nativsql.util.DeleteQuery;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.Fields;
import ovh.heraud.nativsql.util.FindQuery;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.util.OneToManyAssociation;
import ovh.heraud.nativsql.util.ComputedFieldInfo;
import ovh.heraud.nativsql.util.OrderBy;
import ovh.heraud.nativsql.util.ReflectionUtils;
import ovh.heraud.nativsql.util.ReflectionUtils.Getter;
import ovh.heraud.nativsql.util.SqlUtils;
import ovh.heraud.nativsql.util.TypeInfo;

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
    private RowMapperFactory rowMapperFactory;

    @Autowired
    private AnnotationManager annotationManager;

    @Autowired
    private DbOperationLogger dbOperationLogger;

    private DatabaseDialect databaseDialect;

    private final IdentifierConverter identifierConverter = new SnakeCaseIdentifierConverter();

    private final Class<T> entityClass;

    @Autowired(required = false)
    protected ApplicationContext applicationContext;

    private Fields entityFields;

    private final NamedParamSqlCaster namedParamSqlCaster = new NamedParamSqlCaster();

    protected String tableName;

    private DataSource dataSource;

    protected GenericRepository() {
        this.entityClass = getEntityClass();
        this.entityFields = ReflectionUtils.getFields(entityClass);

    }

    /**
     * Constructor for programmatic instantiation (without Spring).
     * Used by subclasses that need to be instantiated directly.
     *
     * @param entityClass       the entity class
     * @param tableName         the database table name
     * @param rowMapperFactory  the row mapper factory
     * @param annotationManager the annotation manager
     * @param dbOperationLogger the database operation logger
     */
    protected GenericRepository(Class<T> entityClass, String tableName,
            RowMapperFactory rowMapperFactory,
            AnnotationManager annotationManager, DbOperationLogger dbOperationLogger) {
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.entityFields = ReflectionUtils.getFields(entityClass);
        this.rowMapperFactory = rowMapperFactory;
        this.annotationManager = annotationManager;
        this.dbOperationLogger = dbOperationLogger;
    }

    @PostConstruct
    protected void initJdbcTemplate() {
        if (this.getProvidedDataSource() != null) {
            this.jdbcTemplate = new NamedParameterJdbcTemplate(getProvidedDataSource());
        }
        this.databaseDialect = getDatabaseDialectInstance();
    }

    /**
     * Reinitialize JdbcTemplate after DataSource injection.
     * Called by tests when the DataSource is changed at runtime.
     */
    public void reinitializeJdbcTemplate() {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(getProvidedDataSource());
        this.databaseDialect = getDatabaseDialectInstance();
    }

    protected DataSource getProvidedDataSource() {
        if (dataSource == null) {
            dataSource = getDataSource();
            return dataSource;
        }
        return dataSource;
    }

    protected abstract DataSource getDataSource();

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    abstract protected Class<T> getEntityClass();

    protected abstract DatabaseDialect getDatabaseDialectInstance();

    /**
     * Returns the name of the database table.
     * Uses the tableName field if provided via constructor, otherwise must be
     * overridden by subclasses.
     */
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
    public AnnotationManager getAnnotationManager() {
        return annotationManager;
    }

    /**
     * Computes and applies any {@code @OnInsert}/{@code @OnUpdate} field not already
     * listed in {@code columns}: invokes its provider, writes the value onto
     * {@code entity} immediately, and appends the field name to the returned column
     * list. Shared by {@link #insert(Object, String...)} and
     * {@link #update(Object, String...)}, which differ only in which computed-field
     * info list and annotation name they pass in.
     *
     * @param entity            the entity to write computed values onto
     * @param columns           the columns explicitly requested by the caller
     * @param computedFieldInfos the {@code @OnInsert}/{@code @OnUpdate} fields declared on the entity
     * @param annotationName    the annotation's simple name, for the null-value exception message
     * @param appliedFieldNames output: filled with the names of the fields actually auto-applied
     * @return {@code columns} plus the auto-applied field names
     * @throws NativSQLException if a provider returns null
     */
    private String[] applyComputedFields(T entity, String[] columns, List<ComputedFieldInfo> computedFieldInfos,
            String annotationName, List<String> appliedFieldNames) {
        List<String> effectiveColumnsList = new ArrayList<>(Arrays.asList(columns));
        for (ComputedFieldInfo info : computedFieldInfos) {
            if (Arrays.stream(columns).noneMatch(c -> info.fieldName().equalsIgnoreCase(c))) {
                Object value = info.provider().getValue();
                if (value == null) {
                    throw new NativSQLException("@" + annotationName + " provider must not return null (field '"
                            + info.fieldName() + "')");
                }
                FieldAccessor<Object> field = entityFields.get(info.fieldName());
                field.setValue(entity, value);
                effectiveColumnsList.add(info.fieldName());
                appliedFieldNames.add(info.fieldName());
            }
        }
        return effectiveColumnsList.toArray(new String[0]);
    }

    /**
     * Inserts an entity with specified columns using getter method references.
     * Converts getter references to column names and delegates to
     * {@link #insert(Object, String...)}.
     *
     * @param entity  the entity to insert (will be modified with generated ID)
     * @param getters the getter method references (e.g., User::getEmail,
     *                User::getId) — must not be empty
     * @throws NativSQLException if getters array is empty
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
     * <p>
     * Any {@code @OnInsert}-annotated field not already listed in {@code columns} is
     * recomputed via its provider and written onto {@code entity} <strong>before</strong>
     * the SQL statement runs, mirroring {@link #update(Object, String...)}: if the insert
     * subsequently fails, {@code entity} is left holding the new, uncommitted
     * {@code @OnInsert} value(s).
     *
     * @param entity  the entity to insert (will be modified with generated ID)
     * @param columns the property names (camelCase) to insert (must not be empty)
     * @throws NativSQLException if columns array is empty
     */
    public void insert(T entity, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }

        List<ComputedFieldInfo> onInsertInfos = annotationManager.getOnInsertFieldInfos(entityClass);
        List<String> onInsertFieldNames = new ArrayList<>();
        String[] effectiveColumns = applyComputedFields(entity, columns, onInsertInfos, "OnInsert", onInsertFieldNames);

        String columnList = SqlUtils.getColumnsList(identifierConverter, effectiveColumns);

        Map<String, Object> rawParams = extractValues(entity, effectiveColumns);

        String paramList = Arrays.stream(effectiveColumns)
                .map(col -> {
                    if (col == null || col.isEmpty()) {
                        throw new NativSQLException("Column name cannot be null or empty");
                    }
                    FieldAccessor<T> field = entityFields.get(col);
                    return formatParameter(col, field);
                })
                .collect(Collectors.joining(", "));

        String sql = formatQuery("INSERT INTO %s (%s) VALUES (%s)",
                getTableName(), columnList, paramList);

        Map<String, Object> sqlParams = convertParamsToSqlValues(rawParams);
        Map<String, Object> logParams = convertParamsForLogging(rawParams);

        Map<String, Object> onInsertLogValues = new LinkedHashMap<>();
        for (String fieldName : onInsertFieldNames) {
            onInsertLogValues.put(fieldName, logParams.get(fieldName));
        }

        // Try to retrieve generated ID using GeneratedKeyHolder for better reliability
        ID generatedId = dbOperationLogger.executeInsert(getClass(), getTableName(), sql, logParams,
                onInsertLogValues, () -> insertWithGeneratedKey(sql, sqlParams));

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
    protected ID insertWithGeneratedKey(String sql, Map<String, Object> params) {
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

        Object idValue = getDatabaseDialect().getGeneratedKey(keys, ID_COLUMN);

        FieldAccessor<ID> idField = entityFields.get(ID_COLUMN);
        ITypeMapper<ID> idMapper = databaseDialect.getMapper(idField, annotationManager);
        if (idMapper == null) {
            throw new NativSQLException("No type mapper found for ID field: " + idField.getField().getName());
        }
        TypeInfo typeInfo = annotationManager.getTypeInfo(idField);
        ID newIdValue = idMapper.map(ID_COLUMN, idField, typeInfo.getParams(), idValue);
        if (newIdValue == null) {
            throw new NativSQLException("Generated ID value is null after mapping");
        }
        return newIdValue;
    }

    private String formatQuery(String sql, Object... params) {
        if (params == null || params.length == 0) {
            throw new NativSQLException("At least one column must be specified");
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
    protected int executeUpdate(String sql, Map<String, Object> params) {
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
     *                User::getId) — must not be empty
     * @throws NativSQLException if getters array is empty or if the update doesn't
     *                           affect exactly one row
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
     * <p>
     * Any {@code @OnUpdate}-annotated field not already listed in {@code columns} is
     * recomputed via its provider and written onto {@code entity} <strong>before</strong>
     * the SQL statement runs (not after, unlike the row-count/value refresh done for other
     * operations). This means that if the update subsequently fails (exception, or affected
     * row count != 1), {@code entity} is left holding the new, uncommitted {@code @OnUpdate}
     * value(s) rather than the value(s) that existed prior to the call.
     *
     * @param entity  the entity to update
     * @param columns the property names (camelCase) to update (must not be empty)
     * @throws NativSQLException if columns is empty or if the update doesn't affect
     *                           exactly one row
     */
    public void update(T entity, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("Column list cannot be empty");
        }

        List<ComputedFieldInfo> onUpdateInfos = annotationManager.getComputedFieldInfos(entityClass);
        List<String> onUpdateFieldNames = new ArrayList<>();
        String[] effectiveColumns = applyComputedFields(entity, columns, onUpdateInfos, "OnUpdate", onUpdateFieldNames);

        Map<String, Object> rawParams = extractValues(entity, effectiveColumns);
        FieldAccessor<ID> idField = entityFields.get(ID_COLUMN);
        Object id = idField != null ? idField.getValue(entity) : null;
        rawParams.put(ID_COLUMN, id);

        String setClause = Arrays.stream(effectiveColumns)
                .map(col -> {
                    if (col == null || col.isEmpty()) {
                        throw new NativSQLException("Column name cannot be null or empty");
                    }
                    FieldAccessor<ID> field = entityFields.get(col);
                    return identifierConverter.toDB(col) + " = " + formatParameter(col, field);
                })
                .collect(Collectors.joining(", "));

        String idColumnSnake = identifierConverter.toDB(ID_COLUMN);
        String sql = "UPDATE " + getTableName() + " SET " + setClause + " WHERE " + idColumnSnake + " = :" + ID_COLUMN;

        Map<String, Object> sqlParams = convertParamsToSqlValues(rawParams);
        Map<String, Object> logParams = convertParamsForLogging(rawParams);

        Map<String, Object> onUpdateLogValues = new LinkedHashMap<>();
        for (String fieldName : onUpdateFieldNames) {
            onUpdateLogValues.put(fieldName, logParams.get(fieldName));
        }

        dbOperationLogger.executeUpdate(getClass(), getTableName(), sql, logParams, onUpdateLogValues, () -> {
            int rowsUpdated = executeUpdate(sql, sqlParams);
            if (rowsUpdated != 1) {
                throw new NativSQLException(
                        "Update failed: expected to update exactly 1 row but updated " + rowsUpdated);
            }
        });
    }

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
        deleteById("deleteById", id);
    }

    private void deleteById(String methodName, ID id) {
        String idColumnSnake = identifierConverter.toDB(ID_COLUMN);
        String sql = "DELETE FROM " + getTableName() + " WHERE " + idColumnSnake + " = :" + ID_COLUMN;
        Map<String, Object> params = getMap(ID_COLUMN, id);

        dbOperationLogger.execute(getClass(), "deleteById", "DELETE", getTableName(), sql, params, () -> {
            int rowsDeleted = executeUpdate(sql, params);
            if (rowsDeleted != 1) {
                throw new NativSQLException(
                        "Delete failed: expected to delete exactly 1 row but deleted " + rowsDeleted);
            }
        });
    }

    /**
     * Deletes an entity by its ID (assumes ID column is named "id").
     *
     * @param entity the entity to delete
     */
    public void delete(T entity) {
        FieldAccessor<ID> idField = entityFields.get(ID_COLUMN);

        ID id = idField.getValue(entity);
        if (id == null) {
            throw new NativSQLException("Entity ID cannot be null for delete operation");
        }
        deleteById("delete", id);
    }

    /**
     * Deletes exactly 1 row matching the given query.
     * Throws NativSQLException if 0 or more than 1 row is deleted,
     * causing any active transaction to roll back.
     */
    public void delete(DeleteQuery<T, ID> query) {
        String sql = query.buildString(identifierConverter);
        Map<String, Object> params = convertParamsToSqlValues(query.getParameters());
        dbOperationLogger.execute(getClass(), "delete", "DELETE", getTableName(), sql, params, () -> {
            int rowsDeleted = executeUpdate(sql, params);
            if (rowsDeleted != 1) {
                throw new NativSQLException(
                        "delete failed: expected to delete exactly 1 row but deleted " + rowsDeleted);
            }
        });
    }

    /**
     * Deletes exactly 1 row where the given property equals the given value.
     * Throws NativSQLException if 0 or more than 1 row is deleted,
     * causing any active transaction to roll back.
     */
    public final void deleteByProperty(Getter<T> getter, Object value) {
        delete(newDeleteQuery().whereAndEquals(getter, value));
    }

    /** @see #deleteByProperty(Getter, Object) */
    public void deleteByProperty(String property, Object value) {
        delete(newDeleteQuery().whereAndEquals(property, value));
    }

    public void deleteAll(DeleteQuery<T, ID> query) {
        String sql = query.buildString(identifierConverter);
        Map<String, Object> params = convertParamsToSqlValues(query.getParameters());
        dbOperationLogger.execute(getClass(), "deleteAll", "DELETE", getTableName(), sql, params,
                () -> executeUpdate(sql, params));
    }

    public final void deleteAllByProperty(Getter<T> getter, Object value) {
        deleteAll(newDeleteQuery().whereAndEquals(getter, value));
    }

    public void deleteAllByProperty(String property, Object value) {
        deleteAll(newDeleteQuery().whereAndEquals(property, value));
    }

    /**
     * Counts the rows matching the given query.
     * Returns 0 when no rows match — never throws based on the result count.
     */
    protected long count(CountQuery<T, ID> query) {
        String sql = query.buildString(identifierConverter);
        Map<String, Object> params = convertParamsToSqlValues(query.getParameters());
        return dbOperationLogger.execute(getClass(), "count", "SELECT", getTableName(), sql, params,
                () -> jdbcTemplate.queryForObject(sql, params, Long.class));
    }

    /**
     * Counts every row in the table (no WHERE conditions).
     */
    public long countAll() {
        return count(newCountQuery());
    }

    /**
     * Counts rows where the given property equals the given value.
     */
    public final long countByProperty(Getter<T> getter, Object value) {
        return count(newCountQuery().whereAndEquals(getter, value));
    }

    /** @see #countByProperty(Getter, Object) */
    public long countByProperty(String property, Object value) {
        return count(newCountQuery().whereAndEquals(property, value));
    }

    /**
     * Tests whether at least one row matches the given query's WHERE conditions.
     * Uses a dialect-specific EXISTS statement — short-circuits on the first
     * match, unlike count().
     */
    protected boolean exists(ExistsQuery<T, ID> query) {
        String innerSql = query.buildString(identifierConverter);
        String sql = databaseDialect.buildExistsQuery(innerSql);
        Map<String, Object> params = convertParamsToSqlValues(query.getParameters());
        Object raw = dbOperationLogger.execute(getClass(), "exists", "SELECT", getTableName(), sql, params,
                () -> jdbcTemplate.queryForObject(sql, params, Object.class));
        return databaseDialect.extractExistsResult(raw);
    }

    /**
     * Tests whether at least one row exists in the table (no WHERE conditions).
     */
    public boolean existsAny() {
        return exists(newExistsQuery());
    }

    /**
     * Tests whether at least one row exists where the given property equals the given value.
     */
    public final boolean existsByProperty(Getter<T> getter, Object value) {
        return exists(newExistsQuery().whereAndEquals(getter, value));
    }

    /** @see #existsByProperty(Getter, Object) */
    public boolean existsByProperty(String property, Object value) {
        return exists(newExistsQuery().whereAndEquals(property, value));
    }

    /**
     * Finds an entity by ID with specified columns using getter method references
     * (assumes ID column is named "id").
     * Converts getter references to column names and delegates to
     * {@link #findById(Object, String...)}.
     *
     * @param id      the ID value
     * @param getters the getter method references (e.g., User::getEmail,
     *                User::getId) — must not be empty
     * @return the entity or null if not found
     * @throws NativSQLException if getters array is empty
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
                        .select(ensureIdColumnSelected(columns))
                        .whereAndEquals(ID_COLUMN, id));
    }

    /**
     * Finds an entity by ID with specified columns using getter method references
     * (assumes ID column is named "id").
     * Converts getter references to column names and delegates to
     * {@link #findOptionalById(Object, String...)}.
     *
     * @param id      the ID value
     * @param getters the getter method references (e.g., User::getEmail,
     *                User::getId) — must not be empty
     * @return an {@link Optional} containing the entity, or empty if not found
     * @throws NativSQLException if getters array is empty
     * @see #findOptionalById(Object, String...)
     */
    @SafeVarargs
    public final Optional<T> findOptionalById(Object id, Getter<T>... getters) {
        return Optional.ofNullable(findById(id, getters));
    }

    /**
     * Finds an entity by ID with specified columns (assumes ID column is named
     * "id").
     *
     * @param id      the ID value
     * @param columns the property names (camelCase) to retrieve (must not be empty)
     * @return an {@link Optional} containing the entity, or empty if not found
     * @throws NativSQLException if columns is empty
     */
    public Optional<T> findOptionalById(Object id, String... columns) {
        return Optional.ofNullable(findById(id, columns));
    }

    /**
     * Ensures the id column is part of the given columns, adding it if absent
     * (case-insensitive comparison against {@link #ID_COLUMN}).
     */
    private String[] ensureIdColumnSelected(String[] columns) {
        for (String column : columns) {
            if (ID_COLUMN.equalsIgnoreCase(column)) {
                return columns;
            }
        }
        String[] result = Arrays.copyOf(columns, columns.length + 1);
        result[columns.length] = ID_COLUMN;
        return result;
    }

    /**
     * Finds all entities by a list of IDs with specified columns using getter
     * method references (assumes ID column is named "id").
     * Converts getter references to column names and delegates to
     * {@link #findAllByIds(List, String...)}.
     *
     * @param ids     the list of ID values
     * @param getters the getter method references (e.g., User::getEmail,
     *                User::getId) — must not be empty
     * @return list of matching entities
     * @throws NativSQLException if getters array is empty
     * @see #findAllByIds(List, String...)
     */
    @SafeVarargs
    public final List<T> findAllByIds(List<ID> ids, Getter<T>... getters) {
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
                        .select(ensureIdColumnSelected(columns))
                        .whereAndIn(ID_COLUMN, ids));
    }

    /**
     * Finds an entity by a property value with specified columns using getter
     * method references for both the filter property and the selected columns.
     * Converts the property getter to a property name and delegates to
     * {@link #findByProperty(String, Object, String...)}.
     *
     * @param propertyGetter the getter method reference identifying the property to
     *                       filter by (e.g., User::getEmail)
     * @param value          the value to search for
     * @param columns        the property names (camelCase) to retrieve (must not be
     *                       empty)
     * @return the entity or null if not found
     * @see #findByProperty(String, Object, String...)
     */

    protected final T findByProperty(Getter<T> propertyGetter, Object value, String... columns) {
        return findByProperty(ReflectionUtils.getColumnName(propertyGetter), value, columns);
    }

    /**
     * Finds an entity by a property value using getter method references for both
     * the filter property and the selected columns.
     * Converts the property getter to a property name and delegates to
     * {@link #findByProperty(String, Object, Getter...)}.
     *
     * @param propertyGetter the getter method reference identifying the property to
     *                       filter by (e.g., User::getEmail)
     * @param value          the value to search for
     * @param getters        the getter method references for selected columns
     *                       (e.g.,
     *                       User::getId, User::getEmail)
     * @return the entity or null if not found
     * @see #findByProperty(String, Object, Getter...)
     */
    @SafeVarargs

    protected final T findByProperty(Getter<T> propertyGetter, Object value, Getter<T>... getters) {
        return findByProperty(ReflectionUtils.getColumnName(propertyGetter), value, getters);
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
     * @param columns  the property names (camelCase) to retrieve (must not be
     *                 empty)
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
     * Finds an entity by a property value with specified columns using getter
     * method references for both the filter property and the selected columns.
     * Converts the property getter to a property name and delegates to
     * {@link #findOptionalByProperty(String, Object, String...)}.
     *
     * @param propertyGetter the getter method reference identifying the property to
     *                       filter by (e.g., User::getEmail)
     * @param value          the value to search for
     * @param columns        the property names (camelCase) to retrieve (must not be
     *                       empty)
     * @return an {@link Optional} containing the entity, or empty if not found
     * @see #findOptionalByProperty(String, Object, String...)
     */
    protected final Optional<T> findOptionalByProperty(Getter<T> propertyGetter, Object value, String... columns) {
        return Optional.ofNullable(findByProperty(propertyGetter, value, columns));
    }

    /**
     * Finds an entity by a property value using getter method references for both
     * the filter property and the selected columns.
     * Converts the property getter to a property name and delegates to
     * {@link #findOptionalByProperty(String, Object, Getter...)}.
     *
     * @param propertyGetter the getter method reference identifying the property to
     *                       filter by (e.g., User::getEmail)
     * @param value          the value to search for
     * @param getters        the getter method references for selected columns
     *                       (e.g., User::getId, User::getEmail)
     * @return an {@link Optional} containing the entity, or empty if not found
     * @see #findOptionalByProperty(String, Object, Getter...)
     */
    @SafeVarargs
    protected final Optional<T> findOptionalByProperty(Getter<T> propertyGetter, Object value, Getter<T>... getters) {
        return Optional.ofNullable(findByProperty(propertyGetter, value, getters));
    }

    /**
     * Finds an entity by a property value with specified columns using getter
     * method references.
     * Converts getter references to column names and delegates to
     * {@link #findOptionalByProperty(String, Object, String...)}.
     *
     * @param property the property name (camelCase) to filter by
     * @param value    the value to search for
     * @param getters  the getter method references (e.g., User::getEmail,
     *                 User::getId)
     * @return an {@link Optional} containing the entity, or empty if not found
     * @see #findOptionalByProperty(String, Object, String...)
     */
    @SafeVarargs
    protected final Optional<T> findOptionalByProperty(String property, Object value, Getter<T>... getters) {
        return Optional.ofNullable(findByProperty(property, value, getters));
    }

    /**
     * Finds an entity by a property value with specified columns.
     *
     * @param property the property name (camelCase) to filter by
     * @param value    the value to search for
     * @param columns  the property names (camelCase) to retrieve (must not be
     *                 empty)
     * @return an {@link Optional} containing the entity, or empty if not found
     * @throws NativSQLException if columns is empty
     */
    protected Optional<T> findOptionalByProperty(String property, Object value, String... columns) {
        return Optional.ofNullable(findByProperty(property, value, columns));
    }

    /**
     * Finds all entities by a property value using a getter method reference for
     * the filter property and string column names for the selected columns.
     * Converts the property getter to a property name and delegates to
     * {@link #findAllByProperty(String, Object, String...)}.
     *
     * @param propertyGetter the getter method reference identifying the property to
     *                       filter by (e.g., User::getStatus)
     * @param value          the value to search for
     * @param columns        the property names (camelCase) to retrieve (must not be
     *                       empty)
     * @return list of matching entities
     * @see #findAllByProperty(String, Object, String...)
     */
    protected final List<T> findAllByProperty(Getter<T> propertyGetter, Object value, String... columns) {
        return findAllByProperty(ReflectionUtils.getColumnName(propertyGetter), value, columns);
    }

    /**
     * Finds all entities by a property value using getter method references for
     * both the filter property and the selected columns.
     * Converts the property getter to a property name and delegates to
     * {@link #findAllByProperty(String, Object, Getter...)}.
     *
     * @param propertyGetter the getter method reference identifying the property to
     *                       filter by (e.g., User::getStatus)
     * @param value          the value to search for
     * @param getters        the getter method references for selected columns
     *                       (e.g.,
     *                       User::getId, User::getEmail)
     * @return list of matching entities
     * @see #findAllByProperty(String, Object, Getter...)
     */
    @SafeVarargs
    protected final List<T> findAllByProperty(Getter<T> propertyGetter, Object value, Getter<T>... getters) {
        return findAllByProperty(ReflectionUtils.getColumnName(propertyGetter), value, getters);
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
     * @param columns  the property names (camelCase) to retrieve (must not be
     *                 empty)
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
     * a getter method reference for the filter property and string column names for
     * the selected columns.
     * Converts the property getter to a property name and delegates to
     * {@link #findAllByProperty(String, Object, OrderBy, String...)}.
     *
     * @param propertyGetter the getter method reference identifying the property to
     *                       filter by (e.g., User::getStatus)
     * @param value          the value to search for
     * @param orderBy        the order by clause
     * @param columns        the property names (camelCase) to retrieve (must not be
     *                       empty)
     * @return list of matching entities
     * @see #findAllByProperty(String, Object, OrderBy, String...)
     */
    protected final List<T> findAllByProperty(Getter<T> propertyGetter, Object value, OrderBy orderBy,
            String... columns) {
        return findAllByProperty(ReflectionUtils.getColumnName(propertyGetter), value, orderBy, columns);
    }

    /**
     * Finds all entities by a property value with specified columns and order using
     * getter method references for both the filter property and the selected
     * columns.
     * Converts the property getter to a property name and delegates to
     * {@link #findAllByProperty(String, Object, OrderBy, Getter...)}.
     *
     * @param propertyGetter the getter method reference identifying the property to
     *                       filter by (e.g., User::getStatus)
     * @param value          the value to search for
     * @param orderBy        the order by clause
     * @param getters        the getter method references for selected columns
     *                       (e.g.,
     *                       User::getId, User::getEmail)
     * @return list of matching entities
     * @see #findAllByProperty(String, Object, OrderBy, Getter...)
     */
    @SafeVarargs
    protected final List<T> findAllByProperty(Getter<T> propertyGetter, Object value, OrderBy orderBy,
            Getter<T>... getters) {
        return findAllByProperty(ReflectionUtils.getColumnName(propertyGetter), value, orderBy, getters);
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
    protected final List<T> findAllByProperty(String property, Object value, OrderBy orderBy,
            Getter<T>... getters) {
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
     * Finds all entities by a list of property values (IN clause) using a getter
     * method reference for the filter property and string column names for the
     * selected columns.
     * Converts the property getter to a property name and delegates to
     * {@link #findAllByProperty(String, List, String...)}.
     *
     * @param propertyGetter the getter method reference identifying the property to
     *                       filter by (e.g., User::getStatus)
     * @param values         the list of values to search for (uses IN clause)
     * @param columns        the property names (camelCase) to retrieve (must not be
     *                       empty)
     * @return list of matching entities
     * @see #findAllByProperty(String, List, String...)
     */
    protected final List<T> findAllByProperty(Getter<T> propertyGetter, List<?> values, String... columns) {
        return findAllByProperty(ReflectionUtils.getColumnName(propertyGetter), values, columns);
    }

    /**
     * Finds all entities by a list of property values (IN clause) using getter
     * method references for both the filter property and the selected columns.
     * Converts the property getter to a property name and delegates to
     * {@link #findAllByProperty(String, List, Getter...)}.
     *
     * @param propertyGetter the getter method reference identifying the property to
     *                       filter by (e.g., User::getStatus)
     * @param values         the list of values to search for (uses IN clause)
     * @param getters        the getter method references for selected columns
     *                       (e.g.,
     *                       User::getId, User::getEmail)
     * @return list of matching entities
     * @see #findAllByProperty(String, List, Getter...)
     */
    @SafeVarargs
    protected final List<T> findAllByProperty(Getter<T> propertyGetter, List<?> values, Getter<T>... getters) {
        return findAllByProperty(ReflectionUtils.getColumnName(propertyGetter), values, getters);
    }

    /**
     * Finds all entities by a list of property values using getter method
     * references (for batch loading with IN clause).
     * Converts getter references to column names and delegates to
     * {@link #findAllByProperty(String, List, String...)}.
     *
     * @param property the property name (camelCase) to filter by
     * @param values   the list of values to search for (uses IN clause)
     * @param getters  the getter method references (e.g., User::getEmail,
     *                 User::getId)
     * @return list of matching entities
     * @see #findAllByProperty(String, List, String...)
     */
    @SafeVarargs
    protected final List<T> findAllByProperty(String property, List<?> values, Getter<T>... getters) {
        String[] columns = ReflectionUtils.getColumnNames(getters);
        return findAllByProperty(property, values, columns);
    }

    /**
     * Finds all entities by a list of property values using IN clause (batch
     * loading).
     *
     * @param property the property name (camelCase) to filter by
     * @param values   the list of values to search for (uses IN clause)
     * @param columns  the property names (camelCase) to retrieve (must not be
     *                 empty)
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
     * Finds all entities by a list of property values using IN clause with a getter
     * method reference for the filter property and string column names for the
     * selected columns.
     * More explicit variant of findAllByProperty for batch loading scenarios.
     * Converts the property getter to a property name and delegates to
     * {@link #findAllByPropertyIn(String, List, String...)}.
     *
     * @param propertyGetter the getter method reference identifying the property to
     *                       filter by (e.g., User::getStatus)
     * @param values         the list of values to search for (uses IN clause)
     * @param columns        the property names (camelCase) to retrieve (must not be
     *                       empty)
     * @return list of matching entities
     * @see #findAllByPropertyIn(String, List, String...)
     */
    protected final List<T> findAllByPropertyIn(Getter<T> propertyGetter, List<?> values, String... columns) {
        return findAllByPropertyIn(ReflectionUtils.getColumnName(propertyGetter), values, columns);
    }

    /**
     * Finds all entities by a list of property values using IN clause with getter
     * method references for both the filter property and the selected columns.
     * More explicit variant of findAllByProperty for batch loading scenarios.
     * Converts the property getter to a property name and delegates to
     * {@link #findAllByPropertyIn(String, List, Getter...)}.
     *
     * @param propertyGetter the getter method reference identifying the property to
     *                       filter by (e.g., User::getStatus)
     * @param values         the list of values to search for (uses IN clause)
     * @param getters        the getter method references for selected columns
     *                       (e.g.,
     *                       User::getId, User::getEmail)
     * @return list of matching entities
     * @see #findAllByPropertyIn(String, List, Getter...)
     */
    @SafeVarargs
    protected final List<T> findAllByPropertyIn(Getter<T> propertyGetter, List<?> values, Getter<T>... getters) {
        return findAllByPropertyIn(ReflectionUtils.getColumnName(propertyGetter), values, getters);
    }

    /**
     * Finds all entities by a list of property values using IN clause with
     * specified columns using getter method references.
     * More explicit variant of findAllByProperty for batch loading scenarios.
     * Converts getter references to column names and delegates to
     * {@link #findAllByPropertyIn(String, List, String...)}.
     *
     * @param property the property name (camelCase) to filter by
     * @param values   the list of values to search for (uses IN clause)
     * @param getters  the getter method references (e.g., User::getEmail,
     *                 User::getId)
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
     * @param columns  the property names (camelCase) to retrieve (must not be
     *                 empty)
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
     * Finds an entity by a property expression with specified columns using getter
     * method references.
     * Allows using database expressions like (address).city for composite types.
     * Converts getter references to column names and delegates to
     * {@link #findByPropertyExpression(String, String, Object, String...)}.
     *
     * @param propertyExpression the SQL expression to filter by (e.g.,
     *                           "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param getters            the getter method references (e.g., User::getEmail,
     *                           User::getId)
     * @return the entity or null if not found
     * @see #findByPropertyExpression(String, String, Object, String...)
     */
    @SafeVarargs

    protected final T findByPropertyExpression(String propertyExpression, String paramName, Object value,
            Getter<T>... getters) {
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
     * @param columns            the property names (camelCase) to retrieve (must
     *                           not be empty)
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
     * Finds an entity by a property expression with specified columns using getter
     * method references.
     * Allows using database expressions like (address).city for composite types.
     * Converts getter references to column names and delegates to
     * {@link #findOptionalByPropertyExpression(String, String, Object, String...)}.
     *
     * @param propertyExpression the SQL expression to filter by (e.g.,
     *                           "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param getters            the getter method references (e.g., User::getEmail,
     *                           User::getId)
     * @return an {@link Optional} containing the entity, or empty if not found
     * @see #findOptionalByPropertyExpression(String, String, Object, String...)
     */
    @SafeVarargs
    protected final Optional<T> findOptionalByPropertyExpression(String propertyExpression, String paramName, Object value,
            Getter<T>... getters) {
        return Optional.ofNullable(findByPropertyExpression(propertyExpression, paramName, value, getters));
    }

    /**
     * Finds an entity by a property expression with specified columns.
     * Allows using database expressions like (address).city for composite types.
     *
     * @param propertyExpression the SQL expression to filter by (e.g.,
     *                           "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param columns            the property names (camelCase) to retrieve (must
     *                           not be empty)
     * @return an {@link Optional} containing the entity, or empty if not found
     * @throws NativSQLException if columns is empty
     */
    protected Optional<T> findOptionalByPropertyExpression(String propertyExpression, String paramName, Object value,
            String... columns) {
        return Optional.ofNullable(findByPropertyExpression(propertyExpression, paramName, value, columns));
    }

    /**
     * Finds all entities by a property expression with specified columns using
     * getter method references.
     * Allows using database expressions like (address).city for composite types.
     * Converts getter references to column names and delegates to
     * {@link #findAllByPropertyExpression(String, String, Object, String...)}.
     *
     * @param propertyExpression the SQL expression to filter by (e.g.,
     *                           "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param getters            the getter method references (e.g., User::getEmail,
     *                           User::getId)
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
     * @param columns            the property names (camelCase) to retrieve (must
     *                           not be empty)
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
     * Finds all entities by a property expression with specified columns and order
     * using getter method references.
     * Allows using database expressions like (address).city for composite types.
     * Converts getter references to column names and delegates to
     * {@link #findAllByPropertyExpression(String, String, Object, OrderBy, String...)}.
     *
     * @param propertyExpression the SQL expression to filter by (e.g.,
     *                           "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param orderBy            the order by clause
     * @param getters            the getter method references (e.g., User::getEmail,
     *                           User::getId)
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
     * @param propertyExpression the SQL expression to filter by (e.g.,
     *                           "(address).city")
     * @param paramName          the parameter name to use in the query
     * @param value              the value to search for
     * @param orderBy            the order by clause
     * @param columns            the property names (camelCase) to retrieve (must
     *                           not be empty)
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
     * Converts getter references to column names and delegates to
     * {@link #findAll(String...)}.
     *
     * @param getters the getter method references (e.g., User::getEmail,
     *                User::getId) — must not be empty
     * @return list of all entities
     * @throws NativSQLException if getters array is empty
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
        List<T> results = dbOperationLogger.execute(getClass(), "SELECT", getTableName(), sql, params,
                () -> findAllExternal(sql, params, entityClass));

        T entity = getFirstOrNull(results);
        if (entity == null) {
            return null;
        }

        // Load associations for multiple linked entities using batch loading
        if (entity != null && query.hasAssociations()) {
            loadAssociationsInBatch(List.of(entity), query.getAssociations());
        }

        return entity;
    }

    /**
     * Finds a single entity using a complex FindQuery builder.
     * Loads associations if specified in the query using batch loading.
     *
     * @param query the FindQuery builder with search criteria
     * @return an {@link Optional} containing the first matching entity, or empty if
     *         not found
     */
    protected Optional<T> findOptional(FindQuery<T, ID> query) {
        return Optional.ofNullable(find(query));
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
        return dbOperationLogger.execute(getClass(), "SELECT", getTableName(), sql, params,
                () -> findAllExternal(sql, params, entityClass));
    }

    /**
     * Finds a single entity using a complex FindQuery builder, mapping the result
     * into a subtype of the entity (e.g. a "report" class that extends the entity
     * with extra computed fields via {@link FindQuery#selectExpression}).
     * Loads associations if specified in the query using batch loading.
     *
     * @param <R>         the result type, a subtype of T
     * @param query       the FindQuery builder with search criteria
     * @param resultClass the class to map the result into
     * @return the first matching entity or null if not found
     */
    protected <R extends T> R find(FindQuery<T, ID> query, Class<R> resultClass) {
        // Build SQL query using FindQuery
        String sql = query.buildString(identifierConverter);

        // Get parameters with SQL conversion
        Map<String, Object> params = query.getParameters();

        // Execute query with joins for nested object mapping
        List<R> results = dbOperationLogger.execute(getClass(), "SELECT", getTableName(), sql, params,
                () -> findAllExternal(sql, params, resultClass));

        R entity = getFirstOrNull(results);
        if (entity == null) {
            return null;
        }

        // Load associations for multiple linked entities using batch loading
        if (query.hasAssociations()) {
            loadAssociationsInBatch(List.of(entity), query.getAssociations());
        }

        return entity;
    }

    /**
     * Finds a single entity using a complex FindQuery builder, mapping the result
     * into a subtype of the entity (e.g. a "report" class that extends the entity
     * with extra computed fields via {@link FindQuery#selectExpression}).
     * Loads associations if specified in the query using batch loading.
     *
     * @param <R>         the result type, a subtype of T
     * @param query       the FindQuery builder with search criteria
     * @param resultClass the class to map the result into
     * @return an {@link Optional} containing the first matching entity, or empty if
     *         not found
     */
    protected <R extends T> Optional<R> findOptional(FindQuery<T, ID> query, Class<R> resultClass) {
        return Optional.ofNullable(find(query, resultClass));
    }

    /**
     * Finds entities using a complex FindQuery builder, mapping each result into
     * a subtype of the entity (e.g. a "report" class that extends the entity with
     * extra computed fields via {@link FindQuery#selectExpression}).
     * Does NOT load associations to avoid N+1 queries.
     *
     * @param <R>         the result type, a subtype of T
     * @param query       the FindQuery builder with search criteria
     * @param resultClass the class to map each result into
     * @return the list of matching entities mapped into resultClass
     */
    protected <R extends T> List<R> findAll(FindQuery<T, ID> query, Class<R> resultClass) {
        // Build SQL query using FindQuery
        String sql = query.buildString(identifierConverter);

        // Get parameters with SQL conversion
        Map<String, Object> params = query.getParameters();

        // Execute query with joins for nested object mapping (no association loading to
        // avoid N+1)
        return dbOperationLogger.execute(getClass(), "SELECT", getTableName(), sql, params,
                () -> findAllExternal(sql, params, resultClass));
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

    protected DeleteQuery<T, ID> newDeleteQuery() {
        return DeleteQuery.of(this);
    }

    protected CountQuery<T, ID> newCountQuery() {
        return CountQuery.of(this);
    }

    protected ExistsQuery<T, ID> newExistsQuery() {
        return ExistsQuery.of(this);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Extracts values for specified properties.
     */
    private Map<String, Object> extractValues(T entity, String... properties) {
        Map<String, Object> params = new HashMap<>();

        Set<String> propertySet = new HashSet<>(Arrays.asList(properties));

        for (FieldAccessor<?> fieldAccessor : entityFields.list()) {
            if (propertySet.contains(fieldAccessor.getName())) {
                params.put(fieldAccessor.getName(), fieldAccessor.getValue(entity));
            }
        }

        return params;
    }

    /**
     * Formats a parameter with appropriate SQL casting for enums and composite
     * types using the TypeMapper.
     */
    private <PARAM_T> String formatParameter(String paramName, FieldAccessor<PARAM_T> fieldAccessor) {
        ITypeMapper<PARAM_T> mapper = databaseDialect.getMapper(fieldAccessor, annotationManager);
        if (mapper == null) {
            throw new NativSQLException(
                    "No TypeMapper found for type: " + fieldAccessor.getType().getName() +
                            ". Please ensure the type is properly configured in the database dialect.");
        }
        TypeInfo typeInfo = annotationManager.getTypeInfo(fieldAccessor);
        return mapper.formatParameter(paramName, typeInfo.getParams());
    }

    /**
     * Converts a Java object to its SQL representation based on the declared
     * DbDataType.
     * Handles enums, composite types, JSON types, and value objects.
     * For lists, converts each element in the list using the element's type mapper.
     * Returns null if value is null.
     * If dataType is null, the mapper will use its default behavior.
     */
    private <PARAM_T> Object convertToSqlValue(PARAM_T value, FieldAccessor<?> fieldAccessor,
            TypeInfo typeInfo) {
        if (value == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        ITypeMapper<PARAM_T> mapper = (ITypeMapper<PARAM_T>) databaseDialect.getMapper(fieldAccessor,
                annotationManager);
        if (mapper == null) {
            throw new NativSQLException(
                    "No TypeMapper found for type: " + value.getClass().getName() +
                            ". Please ensure the type is properly configured in the database dialect.");
        }
        return mapper.toDatabase(value, typeInfo.getParams());
    }

    /**
     * Converts all parameters in a map to their SQL representations using type
     * mappers. Checks for @Type annotations on entity fields to determine the
     * appropriate database type for conversion.
     */
    private Map<String, Object> convertParamsForLogging(Map<String, Object> rawParams) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                result.put(fieldName, null);
                continue;
            }
            FieldAccessor<Object> field = entityFields.getOrNull(fieldName);
            if (field != null) {
                TypeInfo typeInfo = annotationManager.getTypeInfo(field);
                if (typeInfo.getParams().containsKey(TypeParamKey.ENCRYPTED)) {
                    result.put(fieldName, value);
                    continue;
                }
                result.put(fieldName, convertToSqlValue(value, field, typeInfo));
            } else {
                result.put(fieldName, value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertParamsToSqlValues(Map<String, Object> params) {
        Map<String, Object> converted = new HashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            FieldAccessor<Object> declaredField = entityFields.getOrNull(entry.getKey());
            boolean isCollectionTypedColumn = declaredField != null
                    && Collection.class.isAssignableFrom(declaredField.getType());
            if (entry.getValue() instanceof List<?> list && !isCollectionTypedColumn) {
                List<Object> convertedList = convertListParams((List<Object>) list);
                converted.put(entry.getKey(), convertedList);
            } else {
                if (entry.getValue() == null || entry.getValue() instanceof NullableParam) {
                    converted.put(entry.getKey(), null);
                } else {
                    FieldAccessor<Object> field = declaredField != null ? declaredField
                            : new FieldAccessor<Object>(entry.getValue().getClass());
                    TypeInfo typeInfo = annotationManager.getTypeInfo(field);
                    converted.put(entry.getKey(), convertToSqlValue(entry.getValue(), field, typeInfo));
                }
            }
        }
        return converted;

    }

    private List<Object> convertListParams(List<Object> list) {
        return list.stream()
                .map(item -> {
                    FieldAccessor<Object> field = new FieldAccessor<>(item.getClass());
                    TypeInfo typeInfo = annotationManager.getTypeInfo(field);
                    return convertToSqlValue(item, field, typeInfo);
                })
                .collect(Collectors.toList());
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
        return results.isEmpty() ? (E) null : results.get(0);
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
    private void loadAssociationsInBatch(List<? extends T> entities, List<Association> associations) {
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
    private <SUBT extends IEntity<ID>> void loadAssociationInBatch(List<? extends T> entities,
            Association association) {
        FieldAccessor<List<SUBT>> fieldAccessor = entityFields.get(association.getName());
        OneToManyAssociation associationAnnotation = annotationManager.getOneToManyInfo(fieldAccessor);
        if (associationAnnotation == null) {
            throw new NativSQLException("Field is not annotated with @OneToMany: " + association.getName());
        }

        // Get the repository for the associated entity
        @SuppressWarnings("unchecked")
        GenericRepository<SUBT, ID> repository = (GenericRepository<SUBT, ID>) applicationContext
                .getBean(associationAnnotation.getRepositoryClass());
        if (repository == null) {
            throw new NativSQLException(
                    "Repository not found for association: " + association.getName() +
                            ". Ensure the repository class is correctly specified in the @OneToMany annotation and is a Spring bean.");
        }

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
            if (entity == null) {
                throw new NativSQLException("Entity cannot be null when loading associations");
            }
            fieldAccessor.setValue(entity, new ArrayList<>());
        }

        // Add associated entities directly to their parent
        for (SUBT associated : allAssociatedEntities) {

            ID parentIdValue = repository.getFieldValue(associated, foreignKeyField);
            T parentEntity = entitiesById.get(parentIdValue);
            if (parentEntity != null) {
                List<SUBT> associatedList = fieldAccessor.getValue(parentEntity);
                if (associatedList == null) {
                    throw new NativSQLException("Entity cannot be null when loading associations");
                }
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

    protected <EXT> EXT findExternal(String sql, Class<EXT> resultClass) {
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

    protected <EXT> EXT findExternal(String sql, Map<String, Object> params,
            Class<EXT> resultClass) {
        List<EXT> results = dbOperationLogger.execute(getClass(), "SELECT", getTableName(), sql, params,
                () -> findAllExternal(sql, params, resultClass));
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
    protected <EXT> List<EXT> findAllExternal(String sql, Class<EXT> resultClass) {
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
    protected <EXT> List<EXT> findAllExternal(String sql, Map<String, Object> params,
            Class<EXT> resultClass) {
        String castSql = namedParamSqlCaster.castNamedParameters(sql, params, entityFields, databaseDialect,
                annotationManager);
        Map<String, Object> convertedParams = convertParamsToSqlValues(params);
        return jdbcTemplate.query(castSql, convertedParams,
                rowMapperFactory.getRowMapper(resultClass, databaseDialect, identifierConverter));
    }

}