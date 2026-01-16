package io.github.pascalheraud.nativsql.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.pascalheraud.nativsql.db.DatabaseDialect;
import io.github.pascalheraud.nativsql.util.FieldAccessor;
import io.github.pascalheraud.nativsql.util.Join;
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
     * Gets or creates a GenericRowMapper for the specified class with JOIN metadata.
     * This version allows mapping of joined table columns to nested objects.
     *
     * @param clazz   the class to create a mapper for
     * @param dialect the database dialect for dialect-specific type mapping
     * @param joins   list of JOIN metadata for nested object mapping
     * @return a GenericRowMapper for the class with JOIN support
     */
    @NonNull
    public <T> GenericRowMapper<T> getRowMapper(Class<T> clazz, DatabaseDialect dialect, List<Join> joins) {
        return createRowMapperWithJoins(clazz, dialect, joins);
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
        }

        return new GenericRowMapper<T>(clazz, simpleProperties, nestedProperties);
    }

    /**
     * Creates a new GenericRowMapper with JOIN metadata support.
     * This allows mapping of columns from joined tables to nested objects.
     */
    @NonNull
    private <T> GenericRowMapper<T> createRowMapperWithJoins(Class<T> clazz, DatabaseDialect dialect, List<Join> joins) {
        List<PropertyMetadata<?>> simpleProperties = new ArrayList<>();
        Map<String, JoinedPropertyMetadata> joinedProperties = new HashMap<>();

        // Get all fields
        for (FieldAccessor fieldAccessor : ReflectionUtils.getFields(clazz).list()) {

            // Check if this field matches one of the joins
            boolean isJoinedField = false;
            for (Join join : joins) {
                if (join.getName().equals(fieldAccessor.getName())) {
                    // This field is part of a join
                    GenericRowMapper<?> delegateMapper = getRowMapper(
                            fieldAccessor.getType(), dialect);

                    joinedProperties.put(fieldAccessor.getName(),
                            new JoinedPropertyMetadata(
                                    fieldAccessor.getName(),
                                    fieldAccessor.getType(),
                                    fieldAccessor,
                                    delegateMapper,
                                    join.getRepository().getTableNameForQuery()));
                    isJoinedField = true;
                    break;
                }
            }

            // If not a joined field, check if it's a simple type
            if (!isJoinedField && fieldAccessor.isSimpleType()) {
                ITypeMapper<?> typeMapper = dialect.getMapper(fieldAccessor.getType());

                if (typeMapper == null) {
                    // Skip properties without a mapper
                    continue;
                }

                simpleProperties.add((PropertyMetadata<?>) new PropertyMetadata<>(
                        fieldAccessor, typeMapper));
            }
        }

        return new GenericRowMapper<T>(clazz, simpleProperties, new HashMap<>(), joinedProperties);
    }
}