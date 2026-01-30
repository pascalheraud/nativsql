package ovh.heraud.nativsql.db.generic;

import java.util.UUID;

import ovh.heraud.nativsql.db.AbstractChainedDialect;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.db.SnakeCaseIdentifierConverter;
import ovh.heraud.nativsql.db.generic.mapper.DefaultTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.EnumStringMapper;
import ovh.heraud.nativsql.db.generic.mapper.UUIDTypeMapper;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * Base dialect with common behavior shared across all database implementations.
 * Provides default implementations that can be overridden by specific dialects.
 *
 * Part of the Chain of Responsibility pattern, serves as the end of the chain.
 * Handles JDBC-supported types and provides sensible defaults for identifier conversion.
 * Can be extended by specialized dialects that want to add database-specific behavior.
 *
 * Type registration methods (registerJsonType, registerEnumType, registerCompositeType)
 * are inherited from AbstractChainedDialect.
 */
public class GenericDialect extends AbstractChainedDialect {

    private final IdentifierConverter identifierConverter;

    /**
     * Create a default dialect with a next dialect to delegate to.
     */
    public GenericDialect(DatabaseDialect nextDialect) {
        super(nextDialect);
        this.identifierConverter = new SnakeCaseIdentifierConverter();
    }

    /**
     * Create a default dialect with no next dialect (end of chain).
     */
    public GenericDialect() {
        super();
        this.identifierConverter = new SnakeCaseIdentifierConverter();
    }

    /**
     * Get the identifier converter used by this dialect.
     */
    public IdentifierConverter getIdentifierConverter() {
        return identifierConverter;
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
}
