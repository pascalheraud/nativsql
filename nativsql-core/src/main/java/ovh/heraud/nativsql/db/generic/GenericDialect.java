package ovh.heraud.nativsql.db.generic;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.NonNull;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.crypt.CryptAlgorithm;
import ovh.heraud.nativsql.db.AbstractChainedDialect;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.db.SnakeCaseIdentifierConverter;
import ovh.heraud.nativsql.db.generic.mapper.BigDecimalTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.GenericJSONTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.BigIntegerTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.BooleanTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.ByteArrayTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.ByteTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.DefaultTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.DoubleTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.EnumStringMapper;
import ovh.heraud.nativsql.db.generic.mapper.FloatTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.IntegerTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.LocalDateTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.LocalDateTimeTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.LongTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.ShortTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.StringTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.UUIDTypeMapper;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.TypeInfo;

/**
 * Base dialect with common behavior shared across all database implementations.
 * Provides default implementations that can be overridden by specific dialects.
 *
 * Part of the Chain of Responsibility pattern, serves as the end of the chain.
 * Handles JDBC-supported types and provides sensible defaults for identifier conversion.
 * Can be extended by specialized dialects that want to add database-specific behavior.
 *
 * Type detection is performed via annotations (@Json, @CompositeType, @EnumMapping)
 * which are managed by AnnotationManager. Legacy registration methods (registerJsonType,
 * registerEnumType, registerCompositeType) are still supported for backward compatibility.
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
     * Checks enum types first, then JSON types, then composite types, then type-specific mappers for numeric types.
     * When the field is annotated with {@code @Type(DbDataType.ENCRYPTED)}, returns an encrypted mapper.
     * Subclasses can override to add dialect-specific mappings.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> ITypeMapper<T> getMapper(FieldAccessor<T> fieldAccessor, @NonNull AnnotationManager annotationManager) {
        Class<T> targetType = (Class<T>) fieldAccessor.getType();

        // Check for ENCRYPTED type first
        TypeInfo typeInfo = annotationManager.getTypeInfo(fieldAccessor);
        if (typeInfo != null && typeInfo.getDataType() == DbDataType.ENCRYPTED) {
            Map<TypeParamKey, Object> params = typeInfo.getParams();
            validateCryptParams(params, fieldAccessor.getName());
            return getEncryptedMapper(targetType, params);
        }

        // Check if it's an enum
        if (targetType.isEnum()) {
            return (ITypeMapper<T>) getEnumMapperHelper((Class<?>) targetType, annotationManager);
        }

        // Check if it's a JSON type via AnnotationManager
        if (annotationManager.getJsonInfo(targetType) != null) {
            return (ITypeMapper<T>) getJsonMapper(targetType);
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
        if (targetType == UUID.class) {
            return (ITypeMapper<T>) new UUIDTypeMapper();
        }
        if (targetType == Long.class) {
            return (ITypeMapper<T>) new LongTypeMapper();
        }
        if (targetType == Integer.class) {
            return (ITypeMapper<T>) new IntegerTypeMapper();
        }
        if (targetType == Double.class) {
            return (ITypeMapper<T>) new DoubleTypeMapper();
        }
        if (targetType == Float.class) {
            return (ITypeMapper<T>) new FloatTypeMapper();
        }
        if (targetType == Short.class) {
            return (ITypeMapper<T>) new ShortTypeMapper();
        }
        if (targetType == Byte.class) {
            return (ITypeMapper<T>) new ByteTypeMapper();
        }
        if (targetType == BigDecimal.class) {
            return (ITypeMapper<T>) new BigDecimalTypeMapper();
        }
        if (targetType == BigInteger.class) {
            return (ITypeMapper<T>) new BigIntegerTypeMapper();
        }
        if (targetType == Boolean.class) {
            return (ITypeMapper<T>) new BooleanTypeMapper();
        }
        if (targetType == String.class) {
            return (ITypeMapper<T>) new StringTypeMapper();
        }
        if (targetType == LocalDate.class) {
            return (ITypeMapper<T>) new LocalDateTypeMapper();
        }
        if (targetType == LocalDateTime.class) {
            return (ITypeMapper<T>) new LocalDateTimeTypeMapper();
        }
        if (targetType == byte[].class) {
            return (ITypeMapper<T>) new ByteArrayTypeMapper();
        }
        if (isJdbcType(targetType)) {
            return new DefaultTypeMapper<T>();
        }
        return null;
    }

    // --- private helpers ---

    /**
     * Validates that all required crypt parameters are present and consistent.
     * KEY is expected to be already resolved by AnnotationManager.
     *
     * @throws NativSQLException if any validation rule is violated
     */
    private void validateCryptParams(Map<TypeParamKey, Object> params, String fieldName) {
        Object algoObj = params.get(TypeParamKey.ALGO);
        if (algoObj == null) {
            throw new NativSQLException("Field '" + fieldName + "': ALGO is mandatory for DbDataType.ENCRYPTED");
        }
        CryptAlgorithm[] algorithms = (CryptAlgorithm[]) algoObj;
        if (algorithms.length == 0) {
            throw new NativSQLException("Field '" + fieldName + "': ALGO must have at least one value");
        }
        CryptAlgorithm primaryAlgo = algorithms[0];

        boolean hasKeyProvider = params.containsKey(TypeParamKey.KEY_PROVIDER);
        boolean hasKey = params.containsKey(TypeParamKey.KEY);
        boolean hasPrefix = params.containsKey(TypeParamKey.PREFIX);
        String prefix = hasPrefix ? (String) params.get(TypeParamKey.PREFIX) : null;

        if (primaryAlgo.isOneWay()) {
            if (hasKeyProvider) {
                throw new NativSQLException("Field '" + fieldName + "': KEY_PROVIDER is not allowed for one-way algorithm " + primaryAlgo);
            }
            if (hasPrefix) {
                throw new NativSQLException("Field '" + fieldName + "': PREFIX is not applicable to one-way algorithm " + primaryAlgo + " [PREFIX_NOT_APPLICABLE]");
            }
        } else {
            if (!hasKeyProvider) {
                throw new NativSQLException("Field '" + fieldName + "': KEY_PROVIDER is mandatory for reversible algorithm " + primaryAlgo);
            }
            if (!hasKey) {
                throw new NativSQLException("Field '" + fieldName + "': encryption key could not be resolved — check KEY_PROVIDER implementation");
            }
            if (!hasPrefix) {
                throw new NativSQLException("Field '" + fieldName + "': PREFIX is mandatory for reversible algorithm " + primaryAlgo);
            }
            if (prefix == null || prefix.isEmpty()) {
                throw new NativSQLException("Field '" + fieldName + "': PREFIX must not be empty (e.g. \"{ENC}\")");
            }
        }

        String formatStr = (String) params.get(TypeParamKey.FORMAT);
        if (formatStr != null && !formatStr.isEmpty()
                && !"STRING".equalsIgnoreCase(formatStr) && !"BINARY".equalsIgnoreCase(formatStr)) {
            throw new NativSQLException("Field '" + fieldName + "': invalid FORMAT value '" + formatStr + "' — must be STRING or BINARY");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ITypeMapper<T> getEncryptedMapper(Class<T> targetType, Map<TypeParamKey, Object> params) {
        if (targetType == String.class)        return (ITypeMapper<T>) new StringTypeMapper(params);
        if (targetType == Integer.class)       return (ITypeMapper<T>) new IntegerTypeMapper(params);
        if (targetType == Long.class)          return (ITypeMapper<T>) new LongTypeMapper(params);
        if (targetType == Short.class)         return (ITypeMapper<T>) new ShortTypeMapper(params);
        if (targetType == Byte.class)          return (ITypeMapper<T>) new ByteTypeMapper(params);
        if (targetType == Float.class)         return (ITypeMapper<T>) new FloatTypeMapper(params);
        if (targetType == Double.class)        return (ITypeMapper<T>) new DoubleTypeMapper(params);
        if (targetType == BigDecimal.class)    return (ITypeMapper<T>) new BigDecimalTypeMapper(params);
        if (targetType == BigInteger.class)    return (ITypeMapper<T>) new BigIntegerTypeMapper(params);
        if (targetType == Boolean.class)       return (ITypeMapper<T>) new BooleanTypeMapper(params);
        if (targetType == UUID.class)          return (ITypeMapper<T>) new UUIDTypeMapper(params);
        if (targetType == LocalDate.class)     return (ITypeMapper<T>) new LocalDateTypeMapper(params);
        if (targetType == LocalDateTime.class) return (ITypeMapper<T>) new LocalDateTimeTypeMapper(params);
        throw new NativSQLException("DbDataType.ENCRYPTED is not supported for type: " + targetType.getName());
    }

    /**
     * Determines if a type is supported by JDBC for direct mapping.
     */
    private boolean isJdbcType(Class<?> type) {
        if (type.isPrimitive() ||
            type == String.class || type == Boolean.class ||
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

    private <E extends Enum<E>> ITypeMapper<E> getEnumMapperHelper(Class<?> enumClass, AnnotationManager annotationManager) {
        @SuppressWarnings("unchecked")
        Class<E> typedEnum = (Class<E>) enumClass;
        return (ITypeMapper<E>) getEnumMapper(typedEnum, annotationManager);
    }

    @Override
    public <E extends Enum<E>> ITypeMapper<E> getEnumMapper(Class<E> enumClass, AnnotationManager annotationManager) {
        return new EnumStringMapper<E>(enumClass);
    }

    @Override
    public <T> ITypeMapper<T> getJsonMapper(Class<T> jsonClass) {
        return new GenericJSONTypeMapper<>(jsonClass);
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
