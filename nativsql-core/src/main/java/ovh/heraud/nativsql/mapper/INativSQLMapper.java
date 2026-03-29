package ovh.heraud.nativsql.mapper;

import java.util.function.Function;

import ovh.heraud.nativsql.db.DatabaseDialect;

/**
 * Interface for configuring type mappings in NativSQL.
 *
 * <p>
 * This interface provides methods to register custom type mappings for database
 * types
 * including JSON/JSONB, enums, composite types, and custom conversions.
 * </p>
 */
public interface INativSQLMapper {

    /**
     * Registers a custom TypeMapper for a specific type.
     *
     * @param type   the Java type to register
     * @param mapper the TypeMapper implementation
     * @param <T>    the type parameter
     */
    <T> void register(Class<T> type, ITypeMapper<T> mapper);

    /**
     * Registers a simple composite mapper: source type -> target type.
     *
     * <p>
     * Example: Converting a String to an Email value object:
     * </p>
     * 
     * <pre>
     * mapper.registerCompositeMapper(Email.class, String.class, Email::new);
     * </pre>
     *
     * @param targetType the target Java type
     * @param sourceType the source type from the database
     * @param converter  function to convert from source to target type
     * @param <S>        the source type parameter
     * @param <T>        the target type parameter
     */
    <S, T> void registerCompositeMapper(
            Class<T> targetType,
            Class<S> sourceType,
            Function<S, T> converter);

    /**
     * Registers a type to be mapped to/from database JSON/JSONB.
     *
     * <p>
     * The type will be automatically serialized to JSON when writing to the
     * database
     * and deserialized when reading from the database.
     * </p>
     *
     * @param type the Java type to map to JSON
     * @param <T>  the type parameter
     */
    <T> void registerJsonType(Class<T> type);

    /**
     * Registers an enum type with its database type name.
     *
     * <p>
     * This enables automatic casting when inserting/updating enum values.
     * </p>
     *
     * <p>
     * Example:
     * </p>
     * 
     * <pre>
     * mapper.registerEnumType(UserStatus.class, "user_status");
     * </pre>
     *
     * @param enumType   the Java enum class
     * @param dbTypeName the database enum type name (e.g., "user_status")
     * @param <E>        the enum type parameter
     */
    <E extends Enum<E>> void registerEnumType(Class<E> enumType, String dbTypeName);

    /**
     * Registers a composite type with its database type name and creates a mapper
     * for it.
     *
     * <p>
     * This enables automatic casting when inserting/updating composite values.
     * </p>
     *
     * <p>
     * Example:
     * </p>
     * 
     * <pre>
     * mapper.registerCompositeType(Address.class, "address_type");
     * </pre>
     *
     * @param compositeType the Java class representing the composite
     * @param dbTypeName    the database composite type name (e.g., "address_type")
     * @param <T>           the type parameter
     */
    <T> void registerCompositeType(Class<T> compositeType, String dbTypeName);

    /**
     * Converts a value to JSON using the specified dialect.
     *
     * @param value   the value to convert
     * @param dialect the database dialect for conversion
     * @return the JSON representation
     */
    Object toJson(Object value, DatabaseDialect dialect);

    /**
     * Converts a value to JSONB using the specified dialect.
     *
     * @param value   the value to convert
     * @param dialect the database dialect for conversion
     * @return the JSONB representation
     */
    Object toJsonb(Object value, DatabaseDialect dialect);

    /**
     * Converts an enum value to SQL using the specified dialect.
     * The dialect will look up the database type name from the TypeRegistry.
     *
     * @param value    the enum value
     * @param enumType the enum class (used for type lookup)
     * @param dialect  the database dialect for conversion
     * @return the SQL representation
     */
    Object convertEnumToSql(Enum<?> value, Class<?> enumType, DatabaseDialect dialect);

    /**
     * Converts a composite value to SQL using the specified dialect.
     *
     * @param value      the composite value
     * @param valueClass the class of the value
     * @param dbTypeName the database type name
     * @param dialect    the database dialect for conversion
     * @return the SQL representation
     */
    Object convertCompositeToSql(Object value, Class<?> valueClass, String dbTypeName, DatabaseDialect dialect);
}
