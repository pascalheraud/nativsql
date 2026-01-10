package io.github.pascalheraud.nativsql.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.pascalheraud.nativsql.exception.SQLException;

/**
 * Utility class for reflection operations.
 * Provides caching mechanisms for getters and setters to improve performance.
 */
public final class ReflectionUtils {

    // Cache for getters: "ClassName:propertyName" -> Method
    private static final Map<String, Method> getterCache = new ConcurrentHashMap<>();

    // Cache for setters: "ClassName:propertyName:ParameterType" -> Method
    private static final Map<String, Method> setterCache = new ConcurrentHashMap<>();

    private ReflectionUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets the getter method for a property (with caching).
     *
     * @param clazz the class containing the property
     * @param propertyName the property name (camelCase)
     * @return the getter method
     * @throws SQLException if no getter is found
     */
    private static Method getGetter(Class<?> clazz, String propertyName) {
        String cacheKey = clazz.getName() + ":" + propertyName;
        return getterCache.computeIfAbsent(cacheKey, key -> findGetter(clazz, propertyName));
    }

    /**
     * Gets the setter method for a property (with caching).
     *
     * @param clazz the class containing the property
     * @param propertyName the property name (camelCase)
     * @param parameterType the parameter type of the setter
     * @return the setter method
     * @throws SQLException if no setter is found
     */
    private static Method getSetter(Class<?> clazz, String propertyName, Class<?> parameterType) {
        String cacheKey = clazz.getName() + ":" + propertyName + ":" + parameterType.getName();
        return setterCache.computeIfAbsent(cacheKey, key -> findSetter(clazz, propertyName, parameterType));
    }

    /**
     * Finds a getter method for a property.
     */
    private static Method findGetter(Class<?> clazz, String propertyName) {
        // Try getXxx()
        String getterName = "get" + capitalize(propertyName);
        try {
            return clazz.getMethod(getterName);
        } catch (NoSuchMethodException e) {
            // Try isXxx() for booleans
            String booleanGetterName = "is" + capitalize(propertyName);
            try {
                return clazz.getMethod(booleanGetterName);
            } catch (NoSuchMethodException ex) {
                throw new SQLException("No getter found for property: " + propertyName + " in class: " + clazz.getName(), ex);
            }
        }
    }

    /**
     * Finds a setter method for a property.
     */
    private static Method findSetter(Class<?> clazz, String propertyName, Class<?> parameterType) {
        String setterName = "set" + capitalize(propertyName);
        try {
            return clazz.getMethod(setterName, parameterType);
        } catch (NoSuchMethodException e) {
            throw new SQLException("No setter found for property: " + propertyName + " in class: " + clazz.getName(), e);
        }
    }

    /**
     * Invokes a getter method on an object.
     *
     * @param object the object to invoke the getter on
     * @param propertyName the property name (camelCase)
     * @return the value returned by the getter
     * @throws RuntimeException if invocation fails
     */
    public static Object invokeGetter(Object object, String propertyName) {
        Method getter = getGetter(object.getClass(), propertyName);
        try {
            return getter.invoke(object);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new SQLException("Failed to invoke getter for property: " + propertyName, e);
        }
    }

    /**
     * Invokes a setter method on an object.
     *
     * @param object the object to invoke the setter on
     * @param propertyName the property name (camelCase)
     * @param value the value to set
     * @throws RuntimeException if invocation fails
     */
    public static void invokeSetter(Object object, String propertyName, Object value) {
        Class<?> parameterType = value != null ? value.getClass() : Object.class;
        Method setter = getSetter(object.getClass(), propertyName, parameterType);
        try {
            setter.invoke(object, value);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new SQLException("Failed to invoke setter for property: " + propertyName, e);
        }
    }

    /**
     * Checks if a method is a getter.
     *
     * @param method the method to check
     * @return true if the method is a getter, false otherwise
     */
    public static boolean isGetter(Method method) {
        String name = method.getName();
        return (name.startsWith("get") || name.startsWith("is"))
                && method.getParameterCount() == 0
                && !method.getReturnType().equals(void.class)
                && !name.equals("getClass");
    }

    /**
     * Creates FieldAccessors for all declared fields of an object instance.
     *
     * @param instance the object instance
     * @return array of FieldAccessors
     */
    public static FieldAccessor[] getFieldAccessors(Object instance) {
        Field[] fields = instance.getClass().getDeclaredFields();
        FieldAccessor[] accessors = new FieldAccessor[fields.length];
        for (int i = 0; i < fields.length; i++) {
            accessors[i] = new FieldAccessor(fields[i], instance);
        }
        return accessors;
    }

    /**
     * Creates FieldAccessors for all declared fields of a class (without instance).
     * The FieldAccessors will have null as instance.
     *
     * @param clazz the class to get fields from
     * @return array of FieldAccessors
     */
    public static FieldAccessor[] getFieldAccessors(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        FieldAccessor[] accessors = new FieldAccessor[fields.length];
        for (int i = 0; i < fields.length; i++) {
            accessors[i] = new FieldAccessor(fields[i], null);
        }
        return accessors;
    }

    /**
     * Capitalizes the first letter of a string.
     *
     * @param str the string to capitalize
     * @return the capitalized string
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
