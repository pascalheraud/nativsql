package io.github.pascalheraud.nativsql.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.pascalheraud.nativsql.db.DatabaseDialect;
import io.github.pascalheraud.nativsql.util.FieldAccessor;
import io.github.pascalheraud.nativsql.util.ReflectionUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Factory for creating and caching GenericRowMapper instances.
 * Performs class introspection once per type and caches the result.
 */
@Component
public class RowMapperFactory {

    private final Map<Class<?>, GenericRowMapper<?>> cache = new ConcurrentHashMap<>();

    public RowMapperFactory() {
    }

    /**
     * Gets or creates a GenericRowMapper for the specified class.
     *
     * @param clazz   the class to create a mapper for
     * @param dialect the database dialect for dialect-specific type mapping
     * @return a GenericRowMapper for the class
     */
    @SuppressWarnings({ "unchecked", "null" })
    @NonNull
    public <T> GenericRowMapper<T> getRowMapper(Class<T> clazz, DatabaseDialect dialect) {
        return (GenericRowMapper<T>) cache.computeIfAbsent(clazz, cls -> createRowMapper(cls, dialect));
    }

    /**
     * Creates a new GenericRowMapper by introspecting the class.
     */
    @NonNull
    private <T> GenericRowMapper<T> createRowMapper(Class<T> clazz, DatabaseDialect dialect) {
        List<PropertyMetadata<?>> simpleProperties = new ArrayList<>();
        Map<String, NestedPropertyMetadata> nestedProperties = new HashMap<>();

        // Get all public methods
        for (FieldAccessor fieldAccessor : ReflectionUtils.getFields(clazz).list()) {

            if (fieldAccessor.isSimpleType()) {
                ITypeMapper<?> typeMapper = dialect.getMapper(fieldAccessor.getType());

                if (typeMapper == null) {
                    // Skip properties without a mapper
                    continue;
                }

                simpleProperties.add((PropertyMetadata<?>) new PropertyMetadata<>(
                        fieldAccessor, typeMapper));
            }
            // TODO Pascal reste à gérer les jointures
            // else {
            // // Nested object property - delegate to another mapper
            // GenericRowMapper<?> delegateMapper = getRowMapper(propertyType, dialect);

            // nestedProperties.put(propertyName, new NestedPropertyMetadata(
            // propertyName, propertyType, setter, delegateMapper));
            // }
        }

        return new GenericRowMapper<T>(clazz, simpleProperties, nestedProperties);
    }
}