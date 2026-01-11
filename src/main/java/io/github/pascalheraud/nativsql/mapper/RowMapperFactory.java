package io.github.pascalheraud.nativsql.mapper;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;

import io.github.pascalheraud.nativsql.exception.SQLException;

/**
 * Factory for creating and caching GenericRowMapper instances.
 * Performs class introspection once per type and caches the result.
 */
@Component
public class RowMapperFactory {

    private final Map<Class<?>, GenericRowMapper<?>> cache = new ConcurrentHashMap<>();
    private final TypeMapperFactory typeMapperFactory;

    @Autowired
    public RowMapperFactory(TypeMapperFactory typeMapperFactory) {
        this.typeMapperFactory = typeMapperFactory;
    }

    /**
     * Gets or creates a GenericRowMapper for the specified class.
     */
    @SuppressWarnings({ "unchecked", "null" })
    @Nonnull
    public <T> GenericRowMapper<T> getRowMapper(Class<T> clazz) {
        return (GenericRowMapper<T>) cache.computeIfAbsent(clazz, this::createRowMapper);
    }

    /**
     * Creates a new GenericRowMapper by introspecting the class.
     */
    @Nonnull
    private <T> GenericRowMapper<T> createRowMapper(Class<T> clazz) {
        List<PropertyMetadata> simpleProperties = new ArrayList<>();
        Map<String, NestedPropertyMetadata> nestedProperties = new HashMap<>();

        // Get all public methods
        for (Method method : clazz.getMethods()) {
            if (!isGetter(method)) {
                continue;
            }

            String propertyName = getPropertyName(method);
            Class<?> propertyType = method.getReturnType();
            Method setter = findSetter(clazz, propertyName, propertyType);

            if (setter == null) {
                // No setter, skip this property
                continue;
            }

            if (isSimpleType(propertyType)) {
                // Simple property
                String columnName = camelToSnake(propertyName);
                TypeMapper<?> typeMapper = typeMapperFactory.getMapper(propertyType);

                simpleProperties.add(new PropertyMetadata(
                        propertyName, columnName, propertyType, setter, typeMapper));
            } else {
                // Nested object property - delegate to another mapper
                GenericRowMapper<?> delegateMapper = getRowMapper(propertyType);

                nestedProperties.put(propertyName, new NestedPropertyMetadata(
                        propertyName, propertyType, setter, delegateMapper));
            }
        }

        return new GenericRowMapper<>(clazz, simpleProperties, nestedProperties);
    }

    /**
     * Checks if a method is a getter.
     */
    private boolean isGetter(Method method) {
        String name = method.getName();
        return (name.startsWith("get") || name.startsWith("is"))
                && method.getParameterCount() == 0
                && !method.getReturnType().equals(void.class)
                && !name.equals("getClass")
                && Modifier.isPublic(method.getModifiers());
    }

    /**
     * Extracts the property name from a getter method.
     */
    private String getPropertyName(Method getter) {
        String name = getter.getName();

        if (name.startsWith("get")) {
            return decapitalize(name.substring(3));
        } else if (name.startsWith("is")) {
            return decapitalize(name.substring(2));
        }

        throw new IllegalArgumentException("Not a getter: " + name);
    }

    /**
     * Finds the setter method for a property.
     */
    private Method findSetter(Class<?> clazz, String propertyName, Class<?> propertyType) {
        String setterName = "set" + capitalize(propertyName);

        try {
            return clazz.getMethod(setterName, propertyType);
        } catch (NoSuchMethodException e) {
            throw new SQLException("No setter found: " + setterName + " for class: " + clazz.getName(), e);
        }
    }

    /**
     * Checks if a type is a "simple" type (primitive, wrapper, String, Date, etc.)
     * that should be mapped directly rather than recursively.
     */
    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || type.isEnum()
                || type.getName().startsWith("java.lang.")
                || type.getName().startsWith("java.math.")
                || type.getName().startsWith("java.time.")
                || type.getName().startsWith("java.sql.")
                || type.getName().startsWith("java.util.Date")
                || typeMapperFactory.isJsonType(type) // JSON types are treated as simple
                || hasCustomMapper(type); // Types with custom mappers are simple
    }

    /**
     * Checks if a custom mapper is registered for this type.
     */
    private boolean hasCustomMapper(Class<?> type) {
        // We check if the mapper is not the default Spring JdbcUtils mapper
        // by attempting to get a mapper and seeing if it's custom
        // This is a bit of a hack but works for our purposes
        try {
            TypeMapper<?> mapper = typeMapperFactory.getMapper(type);
            return mapper != null;
        } catch (Exception e) {
            throw new SQLException("Failed to check for custom mapper for type: " + type.getName(), e);
        }
    }

    /**
     * Converts camelCase to snake_case.
     */
    private String camelToSnake(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * Capitalizes the first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Decapitalizes the first letter of a string.
     */
    private String decapitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }
}