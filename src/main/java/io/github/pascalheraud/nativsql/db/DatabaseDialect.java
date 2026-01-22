package io.github.pascalheraud.nativsql.db;

import io.github.pascalheraud.nativsql.mapper.ITypeMapper;

/**
 * Abstraction for database-specific SQL operations and type conversions.
 *
 * This interface defines how database-specific features (enum casting, composite types,
 * JSON handling) are formatted in SQL and converted between Java objects and database values.
 *
 * Implementations handle database-specific syntax and type marshalling.
 */
public interface DatabaseDialect {

    /**
     * Gets the appropriate TypeMapper for the given class.
     * Checks enum types, JSON types, and composite types.
     * Returns null if no dialect-specific mapper is found.
     *
     * @param targetType the type to get a mapper for
     * @return a TypeMapper for the type, or null if not found
     * @param <T> the type
     */
    <T> ITypeMapper<T> getMapper(Class<T> targetType);

    /**
     * Creates a TypeMapper for the specified enum class.
     * The mapper handles both reading from and writing to the database.
     *
     * @param enumClass the enum class
     * @return a mapper for the enum
     * @param <E> the enum type
     */
    <E extends Enum<E>> ITypeMapper<E> getEnumMapper(Class<E> enumClass);

    /**
     * Creates a TypeMapper for the specified JSON type class.
     * The mapper handles both reading from and writing to the database as JSON.
     *
     * @param jsonClass the class to map as JSON
     * @return a mapper for JSON
     * @param <T> the type
     */
    <T> ITypeMapper<T> getJsonMapper(Class<T> jsonClass);

    /**
     * Creates a TypeMapper for the specified composite type class.
     * The mapper handles both reading from and writing to the database.
     *
     * @param compositeClass the composite class
     * @return a mapper for the composite
     * @param <T> the type
     */
    <T> ITypeMapper<T> getCompositeMapper(Class<T> compositeClass);

    /**
     * Convert a Java identifier (camelCase) to a database identifier.
     * Default implementation converts to snake_case (e.g., "firstName" → "first_name").
     *
     * @param javaIdentifier the Java field name in camelCase
     * @return the database column name
     */
    String javaToDBIdentifier(String javaIdentifier);

    /**
     * Convert a database identifier to a Java identifier (camelCase).
     * Default implementation converts from snake_case (e.g., "first_name" → "firstName").
     *
     * @param dbIdentifier the database column name
     * @return the Java field name in camelCase
     */
    String dbToJavaIdentifier(String dbIdentifier);
}
