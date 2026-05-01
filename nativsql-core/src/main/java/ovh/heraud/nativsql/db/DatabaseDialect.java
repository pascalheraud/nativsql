package ovh.heraud.nativsql.db;

import java.util.Map;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.generic.mapper.BigDecimalTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.BigIntegerTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.BooleanTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.ByteArrayTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.ByteTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.DefaultTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.DoubleTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.FloatTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.GenericJSONTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.IntegerTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.LocalDateTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.LocalDateTimeTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.LongTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.ShortTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.StringTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.UUIDTypeMapper;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * Abstraction for database-specific SQL operations and type conversions.
 *
 * This interface defines how database-specific features (enum casting,
 * composite types,
 * JSON handling) are formatted in SQL and converted between Java objects and
 * database values.
 *
 * Implementations handle database-specific syntax and type marshalling.
 */
public interface DatabaseDialect {

    /**
     * Gets the appropriate TypeMapper for the given class.
     * Checks enum types, JSON types, and composite types via AnnotationManager.
     * Returns null if no dialect-specific mapper is found.
     *
     * @param fieldAccessor     the field accessor for the type to get a mapper for
     * @param annotationManager the annotation manager for type detection
     * @return a TypeMapper for the type, or null if not found
     * @param <T> the type
     */
    <T> ITypeMapper<T> getMapper(FieldAccessor<T> fieldAccessor, AnnotationManager annotationManager);

    /**
     * Creates a TypeMapper for the specified enum class.
     * The mapper handles both reading from and writing to the database.
     *
     * @param enumClass         the enum class
     * @param annotationManager the annotation manager for type detection
     * @return a mapper for the enum
     * @param <E> the enum type
     */
    <E extends Enum<E>> ITypeMapper<E> getEnumMapper(Class<E> enumClass, AnnotationManager annotationManager);

    /**
     * Creates a TypeMapper for the specified JSON type class.
     * The mapper handles both reading from and writing to the database as JSON.
     *
     * Default implementation uses GenericJSONTypeMapper which handles JSON
     * serialization/deserialization via Jackson for databases that store JSON as
     * String
     * (MySQL, MariaDB, Oracle, etc).
     *
     * @param jsonClass the class to map as JSON
     * @return a mapper for JSON
     * @param <T> the type
     */
    default <T> ITypeMapper<T> getJsonMapper() {
        return new GenericJSONTypeMapper<>();
    }

    // FIXME Move these methods to GenericDialect
    default ITypeMapper<String> getStringMapper() {
        return new StringTypeMapper();
    }

    default ITypeMapper<Long> getLongMapper() {
        return new LongTypeMapper();
    }

    default ITypeMapper<Integer> getIntegerMapper() {
        return new IntegerTypeMapper();
    }

    default ITypeMapper<Double> getDoubleMapper() {
        return new DoubleTypeMapper();
    }

    default ITypeMapper<Float> getFloatMapper() {
        return new FloatTypeMapper();
    }

    default ITypeMapper<Short> getShortMapper() {
        return new ShortTypeMapper();
    }

    default ITypeMapper<Byte> getByteMapper() {
        return new ByteTypeMapper();
    }

    default ITypeMapper<BigDecimal> getBigDecimalMapper() {
        return new BigDecimalTypeMapper();
    }

    default ITypeMapper<BigInteger> getBigIntegerMapper() {
        return new BigIntegerTypeMapper();
    }

    default ITypeMapper<Boolean> getBooleanMapper() {
        return new BooleanTypeMapper();
    }

    default ITypeMapper<UUID> getUUIDMapper() {
        return new UUIDTypeMapper();
    }

    default ITypeMapper<LocalDate> getLocalDateMapper() {
        return new LocalDateTypeMapper();
    }

    default ITypeMapper<LocalDateTime> getLocalDateTimeMapper() {
        return new LocalDateTimeTypeMapper();
    }

    default ITypeMapper<byte[]> getByteArrayMapper() {
        return new ByteArrayTypeMapper();
    }

    default <T> ITypeMapper<T> getDefaultMapper() {
        return new DefaultTypeMapper<>();
    }

    /**
     * Creates a TypeMapper for the specified composite type class.
     * The mapper handles both reading from and writing to the database.
     *
     * @param compositeClass the composite class
     * @return a mapper for the composite
     * @param <T> the type
     */
    <T> ITypeMapper<T> getCompositeMapper(Class<T> compositeClass, AnnotationManager annotationManager);

    /**
     * Extracts the generated key from the database after an insert operation.
     * 
     * @param <ID>
     * @param keys
     * @param idColumn the name of the ID column to extract from the keys map
     * @return the generated key value, or null if not found
     */
    <ID> ID getGeneratedKey(Map<String, Object> keys, String idColumn);

}
