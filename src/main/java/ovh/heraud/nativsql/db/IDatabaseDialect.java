package ovh.heraud.nativsql.db;

import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * Abstraction for database-specific SQL operations and type conversions.
 *
 * This interface defines how database-specific features (enum casting, composite types,
 * JSON handling) are formatted in SQL and converted between Java objects and database values.
 *
 * Implementations handle database-specific syntax and type marshalling.
 */
public interface IDatabaseDialect {

    /**
     * Gets the appropriate ITypeMapper for the given class.
     * Checks enum types, JSON types, and composite types.
     * Returns null if no dialect-specific mapper is found.
     *
     * @param targetType the type to get a mapper for
     * @return a ITypeMapper for the type, or null if not found
     * @param <T> the type
     */
    <T> ITypeMapper<T> getMapper(Class<T> targetType);

    /**
     * Creates a ITypeMapper for the specified enum class.
     * The mapper handles both reading from and writing to the database.
     *
     * @param enumClass the enum class
     * @return a mapper for the enum
     * @param <E> the enum type
     */
    <E extends Enum<E>> ITypeMapper<E> getEnumMapper(Class<E> enumClass);

    /**
     * Creates a ITypeMapper for the specified JSON type class.
     * The mapper handles both reading from and writing to the database as JSON.
     *
     * @param jsonClass the class to map as JSON
     * @return a mapper for JSON
     * @param <T> the type
     */
    <T> ITypeMapper<T> getJsonMapper(Class<T> jsonClass);

    /**
     * Creates a ITypeMapper for the specified composite type class.
     * The mapper handles both reading from and writing to the database.
     *
     * @param compositeClass the composite class
     * @return a mapper for the composite
     * @param <T> the type
     */
    <T> ITypeMapper<T> getCompositeMapper(Class<T> compositeClass);
}
