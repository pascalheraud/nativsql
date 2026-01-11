package io.github.pascalheraud.nativsql.db;

import io.github.pascalheraud.nativsql.mapper.TypeMapper;

/**
 * Registry for managing type mappings and type information across the database abstraction layer.
 *
 * This interface separates the concern of type registration and lookup from database-specific
 * conversions (which are handled by DatabaseDialect).
 */
public interface TypeRegistry {

    /**
     * Registers a database type name for an enum class.
     *
     * @param enumType the enum class
     * @param dbTypeName the database-specific type name (e.g., "user_status" for Postgres)
     */
    void registerEnumType(Class<? extends Enum<?>> enumType, String dbTypeName);

    /**
     * Registers a database type name for a composite type class.
     *
     * @param compositeType the composite type class
     * @param dbTypeName the database-specific type name (e.g., "address_type" for Postgres)
     */
    void registerCompositeType(Class<?> compositeType, String dbTypeName);

    /**
     * Registers a class as a JSON/JSONB type.
     *
     * @param jsonType the class to register as JSON type
     */
    void registerJsonType(Class<?> jsonType);

    /**
     * Checks if a type is registered as an enum type.
     *
     * @param type the type to check
     * @return true if the type is registered as an enum
     */
    boolean isEnumType(Class<?> type);

    /**
     * Checks if a type is registered as a composite type.
     *
     * @param type the type to check
     * @return true if the type is registered as a composite type
     */
    boolean isCompositeType(Class<?> type);

    /**
     * Checks if a type is registered as a JSON type.
     *
     * @param type the type to check
     * @return true if the type is registered as JSON type
     */
    boolean isJsonType(Class<?> type);

    /**
     * Gets the database-specific type name for a registered enum class.
     *
     * @param enumType the enum class
     * @return the database type name
     * @throws IllegalArgumentException if the type is not registered
     */
    String getEnumDbType(Class<?> enumType);

    /**
     * Gets the database-specific type name for a registered composite type class.
     *
     * @param compositeType the composite type class
     * @return the database type name
     * @throws IllegalArgumentException if the type is not registered
     */
    String getCompositeDbType(Class<?> compositeType);

    /**
     * Gets the TypeMapper for a type.
     *
     * @param type the type to get the mapper for
     * @return the TypeMapper for the type
     * @throws IllegalArgumentException if no mapper is registered
     */
    <T> TypeMapper<T> getMapper(Class<T> type);

    /**
     * Registers a custom TypeMapper for a type.
     *
     * @param type the type to register the mapper for
     * @param mapper the TypeMapper instance
     */
    <T> void register(Class<T> type, TypeMapper<T> mapper);
}
