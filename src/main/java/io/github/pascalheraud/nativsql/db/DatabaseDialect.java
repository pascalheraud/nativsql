package io.github.pascalheraud.nativsql.db;

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
     * Formats a parameter reference for enum type casting in SQL.
     *
     * @param paramName the parameter name (without colon prefix)
     * @param dbTypeName the database type name for the enum (e.g., "user_status" for Postgres)
     * @return the formatted parameter with type casting (e.g., "(:paramName)::user_status" for Postgres)
     */
    String formatEnumParameter(String paramName, String dbTypeName);

    /**
     * Formats a parameter reference for composite type casting in SQL.
     *
     * @param paramName the parameter name (without colon prefix)
     * @param dbTypeName the database type name for the composite type (e.g., "address_type" for Postgres)
     * @return the formatted parameter with type casting (e.g., "(:paramName)::address_type" for Postgres)
     */
    String formatCompositeParameter(String paramName, String dbTypeName);

    /**
     * Converts a Java enum value to its database representation.
     *
     * @param value the enum value
     * @param dbTypeName the database type name
     * @return the database-specific representation of the enum value
     */
    Object convertEnumToSql(Enum<?> value, String dbTypeName);

    /**
     * Converts a Java composite type object to its database representation.
     *
     * @param value the composite object to convert
     * @param valueClass the class of the composite object
     * @param dbTypeName the database type name
     * @param registry the type registry for looking up field information
     * @return the database-specific representation of the composite value
     * @throws Exception if conversion fails
     */
    Object convertCompositeToSql(Object value, Class<?> valueClass, String dbTypeName,
                                   TypeRegistry registry) throws Exception;

    /**
     * Converts a Java object to its JSON/JSONB database representation.
     *
     * @param value the object to serialize to JSON
     * @return the database-specific JSON representation
     * @throws Exception if conversion fails
     */
    Object convertJsonToSql(Object value) throws Exception;

    /**
     * Parses a composite type value from its database representation.
     *
     * @param dbValue the database value (typically a string or object representation)
     * @param targetType the target Java class for the composite type
     * @param registry the type registry for looking up field information
     * @return the parsed composite object
     * @throws Exception if parsing fails
     */
    <T> T parseCompositeType(Object dbValue, Class<T> targetType,
                              TypeRegistry registry) throws Exception;

    /**
     * Parses an enum value from its database representation.
     *
     * @param dbValue the database value (typically an enum object or string)
     * @param enumClass the target enum class
     * @return the parsed enum value
     * @throws Exception if parsing fails
     */
    <E extends Enum<E>> E parseEnum(Object dbValue, Class<E> enumClass) throws Exception;

    /**
     * Parses a JSON value from its database representation.
     *
     * @param dbValue the database value (typically a string or object)
     * @param targetType the target Java class
     * @return the parsed JSON object
     * @throws Exception if parsing fails
     */
    Object parseJson(Object dbValue, Class<?> targetType) throws Exception;

    /**
     * Gets the registered database type name for an enum class.
     *
     * @param enumType the enum class
     * @return the database type name, or null if not registered
     */
    String getRegisteredEnumType(Class<?> enumType);

    /**
     * Gets the registered database type name for a composite type class.
     *
     * @param compositeType the composite type class
     * @return the database type name, or null if not registered
     */
    String getRegisteredCompositeType(Class<?> compositeType);
}
