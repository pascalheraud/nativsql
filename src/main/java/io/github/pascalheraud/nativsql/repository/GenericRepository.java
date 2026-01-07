package io.github.pascalheraud.nativsql.repository;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.NonNull;

import io.github.pascalheraud.nativsql.mapper.RowMapperFactory;
import io.github.pascalheraud.nativsql.mapper.TypeMapperFactory;

/**
 * Generic repository base class that provides insert and update operations
 * using reflection.
 * Subclasses must implement getTableName() to specify the database table.
 * 
 * @param <T> the entity type
 */
public abstract class GenericRepository<T> {

    protected final NamedParameterJdbcTemplate jdbcTemplate;
    protected final RowMapperFactory rowMapperFactory;
    protected final TypeMapperFactory typeMapperFactory;
    protected final Class<T> entityClass;

    // Cache for getters
    private final Map<String, Method> getterCache = new ConcurrentHashMap<>();

    protected GenericRepository(NamedParameterJdbcTemplate jdbcTemplate,
            RowMapperFactory rowMapperFactory,
            TypeMapperFactory typeMapperFactory,
            Class<T> entityClass) {
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapperFactory = rowMapperFactory;
        this.typeMapperFactory = typeMapperFactory;
        this.entityClass = entityClass;
    }

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
        if (columns.length == 0) {
            throw new IllegalArgumentException(
                    "At least one column must be specified for insert. " +
                            "Provide the property names to insert, e.g., insert(user, \"firstName\", \"email\")");
        }
        Map<String, Object> params = extractValues(entity, columns);
        Map<String, Class<?>> propertyTypes = getPropertyTypes(entity, columns);

        String columnList = params.keySet().stream()
                .map(this::camelToSnake)
                .collect(Collectors.joining(", "));

        String paramList = params.keySet().stream()
                .map(col -> formatParameter(col, propertyTypes.get(col)))
                .collect(Collectors.joining(", "));

        String sql = formatQuery("INSERT INTO %s (%s) VALUES (%s)",
                getTableName(), columnList, paramList);

        return jdbcTemplate.update(sql, params);
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
     * Updates an entity with specified columns.
     *
     * @param entity   the entity to update
     * @param idColumn the ID property name (camelCase)
     * @param columns  the property names (camelCase) to update
     * @return the number of rows updated
     */
    public int update(T entity, String idColumn, String... columns) {
        Map<String, Object> params = extractValues(entity, columns);
        Object idValue = extractValue(entity, idColumn);
        params.put(idColumn, idValue);

        Map<String, Class<?>> propertyTypes = getPropertyTypes(entity, columns);

        String setClause = Arrays.stream(columns)
                .map(col -> camelToSnake(col) + " = " + formatParameter(col, propertyTypes.get(col)))
                .collect(Collectors.joining(", "));

        String sql = formatQuery("UPDATE %s SET %s WHERE %s = :%s",
                getTableName(), setClause, camelToSnake(idColumn), idColumn);

        return jdbcTemplate.update(sql, params);
    }

    /**
     * Deletes an entity by ID.
     *
     * @param idColumn the ID property name (camelCase)
     * @param idValue  the ID value
     * @return the number of rows deleted
     */
    public int delete(String idColumn, Object idValue) {
        String sql = formatQuery("DELETE FROM %s WHERE %s = :%s",
                getTableName(), camelToSnake(idColumn), idColumn);

        return jdbcTemplate.update(sql, getMap(idColumn, idValue));
    }

    @SuppressWarnings("null")
    @NonNull
    private Map<String, Object> getMap(String idColumn, Object idValue) {
        return Map.of(idColumn, idValue);
    }

    /**
     * Deletes an entity by ID (assumes ID column is named "id").
     *
     * @param idValue the ID value
     * @return the number of rows deleted
     */
    public int delete(Object idValue) {
        return delete("id", idValue);
    }

    /**
     * Finds an entity by ID with specified columns (assumes ID column is named
     * "id").
     *
     * @param idValue the ID value
     * @param columns the property names (camelCase) to retrieve
     * @return the entity or null if not found
     */
    public T findById(Object idValue, String... columns) {
        return findByProperty("id", idValue, columns);
    }

    /**
     * Finds an entity by a property value with specified columns.
     *
     * @param property the property name (camelCase) to filter by
     * @param value    the value to search for
     * @param columns  the property names (camelCase) to retrieve
     * @return the entity or null if not found
     */
    public T findByProperty(String property, Object value, String... columns) {
        return findByPropertyExpression(camelToSnake(property), property, value, columns);
    }


    /**
     * Finds all entities by a property value with specified columns.
     *
     * @param property the property name (camelCase) to filter by
     * @param value    the value to search for
     * @param columns  the property names (camelCase) to retrieve
     * @return list of matching entities
     */
    public List<T> findAllByProperty(String property, Object value, String... columns) {
        return findAllByPropertyExpression(camelToSnake(property), property, value, columns);
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
    public T findByPropertyExpression(String propertyExpression, String paramName, Object value, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("At least one column must be specified");
        }

        String columnList = Arrays.stream(columns)
                .map(this::camelToSnake)
                .collect(Collectors.joining(", "));

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
    public List<T> findAllByPropertyExpression(String propertyExpression, String paramName, Object value,
            String... columns) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("At least one column must be specified");
        }

        String columnList = Arrays.stream(columns)
                .map(this::camelToSnake)
                .collect(Collectors.joining(", "));

        String sql = formatQuery("SELECT %s FROM %s WHERE %s = :%s",
                columnList, getTableName(), propertyExpression, paramName);

        return jdbcTemplate.query(sql,
                getMap(paramName, value),
                rowMapperFactory.getRowMapper(entityClass));
    }

    /**
     * Finds all entities with specified columns, ordered by ID.
     *
     * @param columns the property names (camelCase) to retrieve
     * @return list of all entities
     */
    public List<T> findAll(String... columns) {
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("At least one column must be specified");
        }

        String columnList = Arrays.stream(columns)
                .map(this::camelToSnake)
                .collect(Collectors.joining(", "));

        String sql = formatQuery("SELECT %s FROM %s ORDER BY id", columnList, getTableName());

        return jdbcTemplate.query(sql, rowMapperFactory.getRowMapper(entityClass));
    }

    // ==================== Private Helper Methods ====================

    /**
     * Extracts values for specified properties.
     */
    private Map<String, Object> extractValues(T entity, String... properties) {
        Map<String, Object> params = new HashMap<>();

        for (String property : properties) {
            Method getter = getGetter(property);
            try {
                Object value = getter.invoke(entity);
                value = convertToSqlValue(value);
                params.put(property, value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to invoke getter for property: " + property, e);
            }
        }

        return params;
    }

    /**
     * Gets the types of specified properties before SQL conversion.
     */
    private Map<String, Class<?>> getPropertyTypes(T entity, String... properties) {
        Map<String, Class<?>> types = new HashMap<>();

        for (String property : properties) {
            Method getter = getGetter(property);
            try {
                Object value = getter.invoke(entity);
                if (value != null) {
                    types.put(property, value.getClass());
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Failed to invoke getter for property: " + property, e);
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
                enumTypeName = camelToSnake(type.getSimpleName());
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
        Method getter = getGetter(property);
        try {
            return getter.invoke(entity);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke getter for property: " + property, e);
        }
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

        // Composite types → String representation
        if (typeMapperFactory.isCompositeType(valueClass)) {
            return convertToCompositeString(value);
        }

        // JSON types → PGobject
        if (typeMapperFactory.isJsonType(valueClass)) {
            return typeMapperFactory.toJsonb(value);
        }

        // Value objects with getValue() method
        if (hasGetValueMethod(valueClass)) {
            try {
                Method getValue = valueClass.getMethod("getValue");
                return getValue.invoke(value);
            } catch (Exception e) {
                // Continue
            }
        }

        // Default: return as-is
        return value;
    }

    /**
     * Converts a Java object to PostgreSQL composite type format.
     * Example: Address("123 St", "Paris", "75001", "France") → "(123
     * St,Paris,75001,France)"
     * Note: Values are NOT quoted - PostgreSQL driver handles the quoting.
     */
    private String convertToCompositeString(Object value) {
        List<String> fieldValues = new ArrayList<>();

        for (Method method : value.getClass().getMethods()) {
            if (isGetter(method)) {
                try {
                    Object fieldValue = method.invoke(value);
                    if (fieldValue == null) {
                        fieldValues.add("");
                    } else {
                        // Escape special characters: quotes and backslashes
                        String strValue = fieldValue.toString()
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace(",", "\\,");
                        fieldValues.add(strValue);
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    // Skip this field
                }
            }
        }

        return "(" + String.join(",", fieldValues) + ")";
    }

    /**
     * Checks if a class has a getValue() method.
     */
    private boolean hasGetValueMethod(Class<?> clazz) {
        try {
            Method method = clazz.getMethod("getValue");
            return method.getReturnType() != void.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Gets the getter method for a property (with caching).
     */
    private Method getGetter(String propertyName) {
        return getterCache.computeIfAbsent(propertyName, prop -> {
            // Try getXxx()
            String getterName = "get" + capitalize(prop);
            try {
                return entityClass.getMethod(getterName);
            } catch (NoSuchMethodException e) {
                // Try isXxx() for booleans
                String booleanGetterName = "is" + capitalize(prop);
                try {
                    return entityClass.getMethod(booleanGetterName);
                } catch (NoSuchMethodException ex) {
                    throw new RuntimeException("No getter found for property: " + prop, ex);
                }
            }
        });
    }

    /**
     * Checks if a method is a getter.
     */
    private boolean isGetter(Method method) {
        String name = method.getName();
        return (name.startsWith("get") || name.startsWith("is"))
                && method.getParameterCount() == 0
                && !method.getReturnType().equals(void.class)
                && !name.equals("getClass");
    }

    /**
     * Converts camelCase to snake_case.
     */
    private String camelToSnake(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * Capitalizes the first letter.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}