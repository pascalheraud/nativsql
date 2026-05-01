package ovh.heraud.nativsql.db.generic;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.NonNull;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.AbstractChainedDialect;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.db.SnakeCaseIdentifierConverter;
import ovh.heraud.nativsql.db.generic.mapper.BigDecimalTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.BigIntegerTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.BooleanTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.ByteArrayTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.ByteTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.DefaultTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.DoubleTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.EnumStringMapper;
import ovh.heraud.nativsql.db.generic.mapper.FloatTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.GenericJSONTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.IntegerTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.LocalDateTimeTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.LocalDateTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.LongTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.ShortTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.StringTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.UUIDTypeMapper;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * Base dialect with common behavior shared across all database implementations.
 * Provides default implementations that can be overridden by specific dialects.
 *
 * Part of the Chain of Responsibility pattern, serves as the end of the chain.
 * Handles JDBC-supported types and provides sensible defaults for identifier
 * conversion.
 * Can be extended by specialized dialects that want to add database-specific
 * behavior.
 *
 * Type detection is performed via annotations
 * (@Json, @CompositeType, @EnumMapping)
 * which are managed by AnnotationManager. Legacy registration methods
 * (registerJsonType,
 * registerEnumType, registerCompositeType) are still supported for backward
 * compatibility.
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
     * Returns a mapper only for types that JDBC can handle directly (primitives,
     * strings, dates, etc.)
     * Returns null for complex types that need custom mapping or are joined
     * properties.
     * Checks enum types first, then JSON types, then composite types, then
     * type-specific mappers for numeric types.
     * Encryption is handled transparently by
     * {@link ovh.heraud.nativsql.mapper.AbstractTypeMapper} at
     * map/toDatabase call time using the field's TypeInfo params — no special
     * mapper is needed here.
     * Subclasses can override to add dialect-specific mappings.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> ITypeMapper<T> getMapper(FieldAccessor<T> fieldAccessor, @NonNull AnnotationManager annotationManager) {
        Class<T> targetType = (Class<T>) fieldAccessor.getType();

        if (targetType.isPrimitive()) {
            throw new ovh.heraud.nativsql.exception.NativSQLException(
                    "Field '" + fieldAccessor.getName() + "' has primitive type '" + targetType.getName()
                            + "' — use the boxed type instead (e.g. int → Integer)");
        }

        // Check if it's an enum
        if (targetType.isEnum()) {
            return (ITypeMapper<T>) getEnumMapperHelper((Class<?>) targetType, annotationManager);
        }

        // Check if it's a JSON type via AnnotationManager
        if (annotationManager.getJsonInfo(targetType) != null) {
            return (ITypeMapper<T>) getJsonMapper();
        }

        // Check if it's a composite type via AnnotationManager
        if (annotationManager.getCompositeTypeInfo(targetType) != null) {
            return (ITypeMapper<T>) getCompositeMapper(targetType, annotationManager);
        }

        return getMapperForType(targetType);
    }

    /**
     * Returns a mapper for the given Java type (non-encrypted path).
     * Extracted to a helper so it can be reused when building encrypted mappers.
     */
    @SuppressWarnings("unchecked")
    protected <T> ITypeMapper<T> getMapperForType(Class<T> targetType) {
        if (targetType == UUID.class)
            return (ITypeMapper<T>) getUUIDMapper();
        if (targetType == Long.class)
            return (ITypeMapper<T>) getLongMapper();
        if (targetType == Integer.class)
            return (ITypeMapper<T>) getIntegerMapper();
        if (targetType == Double.class)
            return (ITypeMapper<T>) getDoubleMapper();
        if (targetType == Float.class)
            return (ITypeMapper<T>) getFloatMapper();
        if (targetType == Short.class)
            return (ITypeMapper<T>) getShortMapper();
        if (targetType == Byte.class)
            return (ITypeMapper<T>) getByteMapper();
        if (targetType == BigDecimal.class)
            return (ITypeMapper<T>) getBigDecimalMapper();
        if (targetType == BigInteger.class)
            return (ITypeMapper<T>) getBigIntegerMapper();
        if (targetType == Boolean.class)
            return (ITypeMapper<T>) getBooleanMapper();
        if (targetType == String.class)
            return (ITypeMapper<T>) getStringMapper();
        if (targetType == LocalDate.class)
            return (ITypeMapper<T>) getLocalDateMapper();
        if (targetType == LocalDateTime.class)
            return (ITypeMapper<T>) getLocalDateTimeMapper();
        if (targetType == byte[].class)
            return (ITypeMapper<T>) getByteArrayMapper();
        if (isJdbcType(targetType))
            return getDefaultMapper();
        return null;
    }

    /**
     * Determines if a type is supported by JDBC for direct mapping.
     */
    private boolean isJdbcType(Class<?> type) {
        if (type == String.class || type == Boolean.class ||
                type == Integer.class || type == Long.class ||
                type == Double.class || type == Float.class ||
                type == Short.class || type == Byte.class ||
                type == Character.class) {
            return true;
        }
        return type == java.sql.Date.class || type == java.sql.Time.class ||
                type == java.sql.Timestamp.class || type == java.util.Date.class ||
                type == java.time.LocalDate.class || type == java.time.LocalTime.class ||
                type == java.time.LocalDateTime.class || type == java.time.Instant.class ||
                type == java.math.BigDecimal.class || type == java.math.BigInteger.class ||
                type == byte[].class;
    }

    private <E extends Enum<E>> ITypeMapper<E> getEnumMapperHelper(Class<?> enumClass,
            AnnotationManager annotationManager) {
        @SuppressWarnings("unchecked")
        Class<E> typedEnum = (Class<E>) enumClass;
        return (ITypeMapper<E>) getEnumMapper(typedEnum, annotationManager);
    }

    @Override
    public <E extends Enum<E>> ITypeMapper<E> getEnumMapper(Class<E> enumClass, AnnotationManager annotationManager) {
        return new EnumStringMapper<E>(enumClass);
    }

    @Override
    public ITypeMapper<String> getStringMapper() {
        return new StringTypeMapper();
    }

    @Override
    public ITypeMapper<Long> getLongMapper() {
        return new LongTypeMapper();
    }

    @Override
    public ITypeMapper<Integer> getIntegerMapper() {
        return new IntegerTypeMapper();
    }

    @Override
    public ITypeMapper<Double> getDoubleMapper() {
        return new DoubleTypeMapper();
    }

    @Override
    public ITypeMapper<Float> getFloatMapper() {
        return new FloatTypeMapper();
    }

    @Override
    public ITypeMapper<Short> getShortMapper() {
        return new ShortTypeMapper();
    }

    @Override
    public ITypeMapper<Byte> getByteMapper() {
        return new ByteTypeMapper();
    }

    @Override
    public ITypeMapper<BigDecimal> getBigDecimalMapper() {
        return new BigDecimalTypeMapper();
    }

    @Override
    public ITypeMapper<BigInteger> getBigIntegerMapper() {
        return new BigIntegerTypeMapper();
    }

    @Override
    public ITypeMapper<Boolean> getBooleanMapper() {
        return new BooleanTypeMapper();
    }

    @Override
    public ITypeMapper<UUID> getUUIDMapper() {
        return new UUIDTypeMapper();
    }

    @Override
    public ITypeMapper<LocalDate> getLocalDateMapper() {
        return new LocalDateTypeMapper();
    }

    @Override
    public ITypeMapper<LocalDateTime> getLocalDateTimeMapper() {
        return new LocalDateTimeTypeMapper();
    }

    @Override
    public ITypeMapper<byte[]> getByteArrayMapper() {
        return new ByteArrayTypeMapper();
    }

    @Override
    public <T> ITypeMapper<T> getDefaultMapper() {
        return new DefaultTypeMapper<>();
    }

    @Override
    public <T> ITypeMapper<T> getJsonMapper() {
        return new GenericJSONTypeMapper<>();
    }

    @Override
    public <T> ITypeMapper<T> getCompositeMapper(Class<T> compositeClass, AnnotationManager annotationManager) {
        throw new UnsupportedOperationException(
                "Composite type mapping is not supported by default dialect. " +
                        "Override getCompositeMapper() in your dialect implementation: " + compositeClass.getName());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <ID> ID getGeneratedKey(Map<String, Object> keys, String idColumn) {
        return (ID) keys.get(idColumn);
    }
}
