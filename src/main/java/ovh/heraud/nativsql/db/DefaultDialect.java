package ovh.heraud.nativsql.db;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import ovh.heraud.nativsql.mapper.DefaultTypeMapper;
import ovh.heraud.nativsql.mapper.EnumStringMapper;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.mapper.UUIDTypeMapper;
import org.springframework.jdbc.support.JdbcUtils;

/**
 * Base dialect with common behavior shared across all database implementations.
 * Provides default implementations that can be overridden by specific dialects.
 */
public abstract class DefaultDialect implements DatabaseDialect {

    protected final Map<Class<?>, String> jsonTypes = new HashMap<>();
    protected final Map<Class<?>, String> enumDbTypes = new ConcurrentHashMap<>();
    protected final Map<Class<?>, String> compositeDbTypes = new ConcurrentHashMap<>();

    public <T> void registerJsonType(Class<T> jsonClass) {
        jsonTypes.putIfAbsent(jsonClass, jsonClass.getSimpleName());
    }

    public <E extends Enum<E>> void registerEnumType(Class<E> enumClass, String dbTypeName) {
        enumDbTypes.put(enumClass, dbTypeName);
    }

    public <T> void registerCompositeType(Class<T> compositeClass, String dbTypeName) {
        compositeDbTypes.put(compositeClass, dbTypeName);
    }

    /**
     * Gets the appropriate TypeMapper for the given class.
     * Returns a mapper only for types that JDBC can handle directly (primitives, strings, dates, etc.)
     * Returns null for complex types that need custom mapping or are joined properties.
     * Checks enum types first, then JSON types.
     * Subclasses can override to add dialect-specific mappings.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> ITypeMapper<T> getMapper(Class<T> targetType) {
        // Check if it's an enum
        if (targetType.isEnum()) {
            return (ITypeMapper<T>) getEnumMapperHelper((Class<?>) targetType);
        }

        // Check if it's a registered JSON type
        if (jsonTypes.containsKey(targetType)) {
            return (ITypeMapper<T>) getJsonMapper(targetType);
        }

        // Check if it's a UUID type
        if (targetType == UUID.class) {
            return (ITypeMapper<T>) new UUIDTypeMapper();
        }

        // Check if it's a JDBC-supported type
        if (isJdbcType(targetType)) {
            return new DefaultTypeMapper<T>();
        }

        // For unknown types (complex objects, joined properties), return null
        return null;
    }

    /**
     * Determines if a type is supported by JDBC for direct mapping.
     */
    private boolean isJdbcType(Class<?> type) {
        // Primitive types and their wrappers
        if (type.isPrimitive() ||
            type == String.class ||
            type == Boolean.class ||
            type == Integer.class ||
            type == Long.class ||
            type == Double.class ||
            type == Float.class ||
            type == Short.class ||
            type == Byte.class ||
            type == Character.class) {
            return true;
        }

        // JDBC standard types
        if (type == java.sql.Date.class ||
            type == java.sql.Time.class ||
            type == java.sql.Timestamp.class ||
            type == java.util.Date.class ||
            type == java.time.LocalDate.class ||
            type == java.time.LocalTime.class ||
            type == java.time.LocalDateTime.class ||
            type == java.time.Instant.class ||
            type == java.math.BigDecimal.class ||
            type == java.math.BigInteger.class ||
            type == byte[].class) {
            return true;
        }

        return false;
    }

    /**
     * Helper method to call getEnumMapper with proper type safety.
     */
    private <E extends Enum<E>> ITypeMapper<E> getEnumMapperHelper(Class<?> enumClass) {
        @SuppressWarnings("unchecked")
        Class<E> typedEnum = (Class<E>) enumClass;
        return (ITypeMapper<E>) getEnumMapper(typedEnum);
    }

    @Override
    public <E extends Enum<E>> ITypeMapper<E> getEnumMapper(Class<E> enumClass) {
        return new EnumStringMapper<E>(enumClass);
    }

    @Override
    public <T> ITypeMapper<T> getJsonMapper(Class<T> jsonClass) {
        throw new UnsupportedOperationException(
                "JSON type mapping is not supported by default dialect. " +
                        "Override getJsonMapper() in your dialect implementation: " + jsonClass.getName());
    }

    @Override
    public <T> ITypeMapper<T> getCompositeMapper(Class<T> compositeClass) {
        throw new UnsupportedOperationException(
                "Composite type mapping is not supported by default dialect. " +
                        "Override getCompositeMapper() in your dialect implementation: " + compositeClass.getName());
    }

    @Override
    public String javaToDBIdentifier(String javaIdentifier) {
        return JdbcUtils.convertPropertyNameToUnderscoreName(javaIdentifier);
    }

    @Override
    public String dbToJavaIdentifier(String dbIdentifier) {
        // Convert snake_case to camelCase
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (char c : dbIdentifier.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }
}
