package io.github.pascalheraud.nativsql.mapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.pascalheraud.nativsql.exception.SQLException;

/**
 * Factory for creating and managing TypeMappers for different Java types.
 * Supports custom mappers, enums, JSON types, and composite value objects.
 */
@Component
public class TypeMapperFactory implements INativSQLMapper {
    
    private final Map<Class<?>, TypeMapper<?>> customMappers = new ConcurrentHashMap<>();
    private final Set<Class<?>> jsonTypes = ConcurrentHashMap.newKeySet();
    private final Map<Class<?>, String> enumPgTypes = new ConcurrentHashMap<>();
    private final Map<Class<?>, String> compositePgTypes = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    
    @Autowired
    public TypeMapperFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
                if (value == null) return null;

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
     * Registers a type to be mapped to/from PostgreSQL JSON/JSONB.
     */
    public <T> void registerJsonType(Class<T> type) {
        jsonTypes.add(type);
        
        TypeMapper<T> mapper = (rs, col) -> {
            try {
                Object value = rs.getObject(col);
                if (value == null) return null;
                
                String json;
                
                if (value instanceof PGobject) {
                    PGobject pgObj = (PGobject) value;
                    if ("json".equals(pgObj.getType()) || "jsonb".equals(pgObj.getType())) {
                        json = pgObj.getValue();
                    } else {
                        throw new IllegalArgumentException(
                            "Expected json/jsonb type, got: " + pgObj.getType());
                    }
                } else if (value instanceof String) {
                    json = (String) value;
                } else {
                    throw new IllegalArgumentException(
                        "Cannot convert " + value.getClass() + " to JSON");
                }
                
                return objectMapper.readValue(json, type);
                
            } catch (java.sql.SQLException | JsonProcessingException e) {
                throw new SQLException(
                    "Failed to deserialize JSON to " + type.getSimpleName(), e);
            }
        };
        
        register(type, mapper);
    }
    
    /**
     * Registers an enum type with its PostgreSQL type name.
     * This enables automatic casting when inserting/updating enum values.
     *
     * @param enumType the Java enum class
     * @param pgTypeName the PostgreSQL enum type name (e.g., "user_status")
     */
    public <E extends Enum<E>> void registerEnumType(Class<E> enumType, String pgTypeName) {
        enumPgTypes.put(enumType, pgTypeName);
    }

    /**
     * Gets the PostgreSQL type name for an enum class.
     * Returns null if not registered.
     */
    public String getEnumPgType(Class<?> enumType) {
        return enumPgTypes.get(enumType);
    }

    /**
     * Registers a composite type with its PostgreSQL type name and creates a mapper for it.
     * This enables automatic casting when inserting/updating composite values.
     *
     * @param compositeType the Java class representing the composite
     * @param pgTypeName the PostgreSQL composite type name (e.g., "address_type")
     */
    public <T> void registerCompositeType(Class<T> compositeType, String pgTypeName) {
        compositePgTypes.put(compositeType, pgTypeName);

        // Create a mapper for reading composite types from PostgreSQL
        TypeMapper<T> mapper = (rs, col) -> {
            try {
                Object value = rs.getObject(col);
                if (value == null) return null;

                // PostgreSQL composite types are returned as PGobject
                if (value instanceof PGobject) {
                    PGobject pgObj = (PGobject) value;
                    return parseCompositeType(pgObj.getValue(), compositeType);
                }

                return null;
            } catch (Exception e) {
                throw new SQLException(
                    "Failed to deserialize composite type " + compositeType.getSimpleName(), e);
            }
        };

        register(compositeType, mapper);
    }

    /**
     * Parses a PostgreSQL composite type string into a Java object.
     * Format: "(value1,value2,value3)" → creates object with constructor or setters
     */
    @SuppressWarnings("unchecked")
    private <T> T parseCompositeType(String compositeStr, Class<T> targetType) {
        if (compositeStr == null || compositeStr.isEmpty()) {
            return null;
        }

        // Remove parentheses: "(val1,val2)" → "val1,val2"
        String content = compositeStr.substring(1, compositeStr.length() - 1);

        // Split by comma (handle quoted values if needed)
        String[] parts = content.split(",");

        try {
            // Try to find a constructor that matches the number of fields
            java.lang.reflect.Constructor<?>[] constructors = targetType.getConstructors();
            for (java.lang.reflect.Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == parts.length) {
                    Object[] args = new Object[parts.length];
                    Class<?>[] paramTypes = constructor.getParameterTypes();

                    for (int i = 0; i < parts.length; i++) {
                        String part = parts[i].trim();
                        // Remove quotes if present
                        if (part.startsWith("\"") && part.endsWith("\"")) {
                            part = part.substring(1, part.length() - 1);
                        }
                        args[i] = convertToType(part, paramTypes[i]);
                    }

                    return (T) constructor.newInstance(args);
                }
            }

            throw new SQLException("No suitable constructor found for " + targetType.getSimpleName());

        } catch (Exception e) {
            throw new SQLException("Failed to parse composite type", e);
        }
    }

    /**
     * Converts a string value to the target type.
     */
    private Object convertToType(String value, Class<?> targetType) {
        if (value == null || value.equalsIgnoreCase("null")) {
            return null;
        }

        if (targetType == String.class) {
            return value;
        } else if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(value);
        } else if (targetType == Long.class || targetType == long.class) {
            return Long.parseLong(value);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(value);
        }

        return value;
    }

    /**
     * Gets the PostgreSQL composite type name for a class.
     * Returns null if not registered.
     */
    public String getCompositePgType(Class<?> type) {
        return compositePgTypes.get(type);
    }

    /**
     * Checks if a type is a PostgreSQL composite type.
     */
    public boolean isCompositeType(Class<?> type) {
        return compositePgTypes.containsKey(type);
    }

    /**
     * Checks if a type should be serialized to JSON.
     */
    public boolean isJsonType(Class<?> type) {
        return jsonTypes.contains(type);
    }
    
    /**
     * Serializes an object to PostgreSQL JSONB.
     */
    public PGobject toJsonb(Object value) {
        return toJson(value, "jsonb");
    }
    
    /**
     * Serializes an object to PostgreSQL JSON.
     */
    public PGobject toJson(Object value, String type) {
        if (value == null) return null;
        
        try {
            String json = objectMapper.writeValueAsString(value);
            
            PGobject pgObject = new PGobject();
            pgObject.setType(type);
            pgObject.setValue(json);
            
            return pgObject;
        } catch (JsonProcessingException | java.sql.SQLException e) {
            throw new SQLException("Failed to serialize to " + type.toUpperCase(), e);
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
                Object value = rs.getObject(col);
                if (value == null) return null;

                // PostgreSQL enum types return PGobject
                if (value instanceof PGobject) {
                    String enumValue = ((PGobject) value).getValue();
                    return Enum.valueOf(enumClass, enumValue);
                }

                // Standard String
                String enumValue = value.toString();
                return Enum.valueOf(enumClass, enumValue);

            } catch (java.sql.SQLException e) {
                throw new SQLException("Failed to get enum value for column: " + col, e);
            } catch (IllegalArgumentException e) {
                throw new SQLException(
                    "Invalid enum value for " + enumClass.getSimpleName() + ": " + e.getMessage(), e);
            }
        };
    }
}