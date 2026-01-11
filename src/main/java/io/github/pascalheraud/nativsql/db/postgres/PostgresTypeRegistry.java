package io.github.pascalheraud.nativsql.db.postgres;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.pascalheraud.nativsql.db.TypeRegistry;
import io.github.pascalheraud.nativsql.mapper.TypeMapper;

/**
 * PostgreSQL implementation of TypeRegistry.
 *
 * Manages registration and lookup of PostgreSQL-specific type names for enums,
 * composite types, and JSON types.
 */
public class PostgresTypeRegistry implements TypeRegistry {

    private final Map<Class<?>, String> enumPgTypes = new HashMap<>();
    private final Map<Class<?>, String> compositePgTypes = new HashMap<>();
    private final Set<Class<?>> jsonTypes = new HashSet<>();
    private final Map<Class<?>, TypeMapper<?>> typeMappers = new HashMap<>();

    @Override
    public void registerEnumType(Class<? extends Enum<?>> enumType, String dbTypeName) {
        enumPgTypes.put(enumType, dbTypeName);
    }

    @Override
    public void registerCompositeType(Class<?> compositeType, String dbTypeName) {
        compositePgTypes.put(compositeType, dbTypeName);
    }

    @Override
    public void registerJsonType(Class<?> jsonType) {
        jsonTypes.add(jsonType);
    }

    @Override
    public boolean isEnumType(Class<?> type) {
        return enumPgTypes.containsKey(type);
    }

    @Override
    public boolean isCompositeType(Class<?> type) {
        return compositePgTypes.containsKey(type);
    }

    @Override
    public boolean isJsonType(Class<?> type) {
        return jsonTypes.contains(type);
    }

    @Override
    public String getEnumDbType(Class<?> enumType) {
        String dbTypeName = enumPgTypes.get(enumType);
        if (dbTypeName == null) {
            throw new IllegalArgumentException("Enum type not registered: " + enumType.getName());
        }
        return dbTypeName;
    }

    @Override
    public String getCompositeDbType(Class<?> compositeType) {
        String dbTypeName = compositePgTypes.get(compositeType);
        if (dbTypeName == null) {
            throw new IllegalArgumentException("Composite type not registered: " + compositeType.getName());
        }
        return dbTypeName;
    }

    @Override
    public <T> TypeMapper<T> getMapper(Class<T> type) {
        @SuppressWarnings("unchecked")
        TypeMapper<T> mapper = (TypeMapper<T>) typeMappers.get(type);
        if (mapper == null) {
            throw new IllegalArgumentException("No mapper registered for type: " + type.getName());
        }
        return mapper;
    }

    @Override
    public <T> void register(Class<T> type, TypeMapper<T> mapper) {
        typeMappers.put(type, mapper);
    }
}
