package io.github.pascalheraud.nativsql.mapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.jdbc.support.JdbcUtils;

import io.github.pascalheraud.nativsql.db.DatabaseDialect;
import io.github.pascalheraud.nativsql.db.TypeRegistry;
import io.github.pascalheraud.nativsql.exception.SQLException;

/**
 * Factory for creating and managing TypeMappers for different Java types.
 * Supports custom mappers, enums, JSON types, and composite value objects.
 *
 * Delegates database-specific type handling to DatabaseDialect and TypeRegistry.
 */
public class TypeMapperFactory implements INativSQLMapper {

    private final Map<Class<?>, TypeMapper<?>> customMappers = new ConcurrentHashMap<>();
    private final Set<Class<?>> jsonTypes = ConcurrentHashMap.newKeySet();
    private final Map<Class<?>, String> enumDbTypes = new ConcurrentHashMap<>();
    private final Map<Class<?>, String> compositeDbTypes = new ConcurrentHashMap<>();
    private final DatabaseDialect dialect;
    private final TypeRegistry typeRegistry;

    public TypeMapperFactory(DatabaseDialect dialect, TypeRegistry typeRegistry) {
        this.dialect = dialect;
        this.typeRegistry = typeRegistry;
    }
    
    /**
     * Registers a custom TypeMapper for a specific type.
     */
    public <T> void register(Class<T> type, TypeMapper<T> mapper) {
        customMappers.put(type, mapper);
    }
    
    /**
     * Registers a simple composite mapper: source type -> target type.
     * Example: String -> Email value object
     */
    public <S, T> void registerCompositeMapper(
            Class<T> targetType, 
            Class<S> sourceType,
            Function<S, T> converter) {
        
        TypeMapper<T> mapper = (rs, col) -> {
            try {
                Object value = rs.getObject(col);
                if (value == null) {
                    return null;
                }

                S sourceValue = sourceType.cast(value);
                return converter.apply(sourceValue);

            } catch (java.sql.SQLException e) {
                throw new SQLException("Failed to get column value: " + col, e);
            } catch (ClassCastException e) {
                throw new SQLException(
                    "Cannot convert column value to " + sourceType.getSimpleName(), e);
            }
        };
        
        register(targetType, mapper);
    }
    
    /**
     * Registers a type to be mapped to/from database JSON/JSONB.
     */
    public <T> void registerJsonType(Class<T> type) {
        jsonTypes.add(type);

        @SuppressWarnings("unchecked")
        TypeMapper<T> mapper = (rs, col) -> {
            try {
                Object value = rs.getObject(col);
                if (value == null) {
                    return null;
                }

                return (T) dialect.parseJson(value, type);

            } catch (java.sql.SQLException e) {
                throw new SQLException(
                    "Failed to deserialize JSON to " + type.getSimpleName(), e);
            } catch (Exception e) {
                throw new SQLException(
                    "Failed to deserialize JSON to " + type.getSimpleName(), e);
            }
        };

        register(type, mapper);
    }
    
    /**
     * Registers an enum type with its database type name.
     * This enables automatic casting when inserting/updating enum values.
     *
     * @param enumType the Java enum class
     * @param dbTypeName the database enum type name (e.g., "user_status")
     */
    public <E extends Enum<E>> void registerEnumType(Class<E> enumType, String dbTypeName) {
        enumDbTypes.put(enumType, dbTypeName);
    }

    /**
     * Gets the database type name for an enum class.
     * Returns null if not registered.
     */
    public String getEnumDbType(Class<?> enumType) {
        return enumDbTypes.get(enumType);
    }

    /**
     * Registers a composite type with its database type name and creates a mapper for it.
     * This enables automatic casting when inserting/updating composite values.
     *
     * @param compositeType the Java class representing the composite
     * @param dbTypeName the database composite type name (e.g., "address_type")
     */
    public <T> void registerCompositeType(Class<T> compositeType, String dbTypeName) {
        compositeDbTypes.put(compositeType, dbTypeName);

        // Create a mapper for reading composite types from the database
        TypeMapper<T> mapper = (rs, col) -> {
            try {
                Object value = rs.getObject(col);
                if (value == null) {
                    return null;
                }

                return dialect.parseCompositeType(value, compositeType, typeRegistry);

            } catch (Exception e) {
                throw new SQLException(
                    "Failed to deserialize composite type " + compositeType.getSimpleName(), e);
            }
        };

        register(compositeType, mapper);
    }

    /**
     * Gets the database composite type name for a class.
     * Returns null if not registered.
     */
    public String getCompositeDbType(Class<?> type) {
        return compositeDbTypes.get(type);
    }

    /**
     * Checks if a type is a composite type.
     */
    public boolean isCompositeType(Class<?> type) {
        return compositeDbTypes.containsKey(type);
    }

    /**
     * Checks if a type should be serialized to JSON.
     */
    public boolean isJsonType(Class<?> type) {
        return jsonTypes.contains(type);
    }
    
    /**
     * Serializes an object to database JSON format.
     * Delegates to the database dialect for database-specific JSON conversion.
     */
    public Object toJsonb(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return dialect.convertJsonToSql(value);
        } catch (Exception e) {
            throw new SQLException("Failed to convert to JSONB", e);
        }
    }

    /**
     * Serializes an object to database JSON format.
     * Delegates to the database dialect for database-specific JSON conversion.
     *
     * @param value the object to serialize
     * @param type the JSON type (e.g., "json", "jsonb") - ignored, uses dialect default
     * @return the database-specific JSON representation
     */
    public Object toJson(Object value, String type) {
        if (value == null) {
            return null;
        }
        try {
            return dialect.convertJsonToSql(value);
        } catch (Exception e) {
            throw new SQLException("Failed to convert to JSON", e);
        }
    }
    
    /**
     * Gets a TypeMapper for the specified target type.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> TypeMapper<T> getMapper(Class<T> targetType) {
        // Check custom mappers cache
        TypeMapper<?> mapper = customMappers.get(targetType);
        if (mapper != null) {
            return (TypeMapper<T>) mapper;
        }
        
        // Handle enums
        if (targetType.isEnum()) {
            return (TypeMapper<T>) createEnumMapper((Class<? extends Enum>) targetType);
        }
        
        // Fallback to Spring JdbcUtils
        return (rs, col) -> {
            try {
                int colIndex = rs.findColumn(col);
                return (T) JdbcUtils.getResultSetValue(rs, colIndex, targetType);
            } catch (java.sql.SQLException e) {
                throw new SQLException("Failed to get value for column: " + col, e);
            }
        };
    }
    
    private <E extends Enum<E>> TypeMapper<E> createEnumMapper(Class<E> enumClass) {
        return (rs, col) -> {
            try {
                Object dbValue = rs.getObject(col);
                if (dbValue == null) {
                    return null;
                }

                try {
                    return dialect.parseEnum(dbValue, enumClass);
                } catch (Exception e) {
                    throw new SQLException(
                        "Invalid enum value for " + enumClass.getSimpleName() + ": " + e.getMessage(), e);
                }

            } catch (java.sql.SQLException e) {
                throw new SQLException("Failed to get enum value for column: " + col, e);
            }
        };
    }
}