package ovh.heraud.nativsql.db;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.AnnotationManager;
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
     *
     * @param enumClass         the enum class
     * @param annotationManager the annotation manager for type detection
     * @return a mapper for the enum
     * @param <E> the enum type
     */
    <E extends Enum<E>> ITypeMapper<E> getEnumMapper(Class<E> enumClass, AnnotationManager annotationManager);

    /**
     * Creates a TypeMapper for JSON columns.
     *
     * @return a mapper for JSON
     * @param <T> the type
     */
    <T> ITypeMapper<T> getJsonMapper();

    <T> ITypeMapper<T> getCompositeMapper(Class<T> compositeClass, AnnotationManager annotationManager);

    ITypeMapper<String> getStringMapper();

    ITypeMapper<Long> getLongMapper();

    ITypeMapper<Integer> getIntegerMapper();

    ITypeMapper<Double> getDoubleMapper();

    ITypeMapper<Float> getFloatMapper();

    ITypeMapper<Short> getShortMapper();

    ITypeMapper<Byte> getByteMapper();

    ITypeMapper<BigDecimal> getBigDecimalMapper();

    ITypeMapper<BigInteger> getBigIntegerMapper();

    ITypeMapper<Boolean> getBooleanMapper();

    ITypeMapper<UUID> getUUIDMapper();

    ITypeMapper<LocalDate> getLocalDateMapper();

    ITypeMapper<LocalDateTime> getLocalDateTimeMapper();

    ITypeMapper<byte[]> getByteArrayMapper();

    <T> ITypeMapper<T> getDefaultMapper();

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
