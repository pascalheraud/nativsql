package ovh.heraud.nativsql.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Utility class for reflection operations.
 */
public final class ReflectionUtils {

    private ReflectionUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Finds a getter method for a property.
     *
     * @param clazz the class containing the property
     * @param propertyName the property name (camelCase)
     * @return the getter method
     * @throws NativSQLException if no getter is found
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
                throw new NativSQLException("No getter found for property: " + propertyName + " in class: " + clazz.getName(), ex);
            }
        }
    }

    /**
     * Finds a setter method for a property.
     *
     * @param clazz the class containing the property
     * @param propertyName the property name (camelCase)
     * @param parameterType the parameter type of the setter
     * @return the setter method
     * @throws NativSQLException if no setter is found
     */
    private static Method findSetter(Class<?> clazz, String propertyName, Class<?> parameterType) {
        String setterName = "set" + capitalize(propertyName);
        try {
            return clazz.getMethod(setterName, parameterType);
        } catch (NoSuchMethodException e) {
            throw new NativSQLException("No setter found for property: " + propertyName + " in class: " + clazz.getName(), e);
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
        Method getter = findGetter(object.getClass(), propertyName);
        try {
            return getter.invoke(object);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new NativSQLException("Failed to invoke getter for property: " + propertyName, e);
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
        Method setter = findSetter(object.getClass(), propertyName, parameterType);
        try {
            setter.invoke(object, value);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new NativSQLException("Failed to invoke setter for property: " + propertyName, e);
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
     * Creates a Fields wrapper with all declared fields of a class.
     * Provides fast lookup by field name via a map.
     *
     * @param clazz the class to get fields from
     * @return Fields wrapper with all field accessors
     */
    public static Fields getFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        List<FieldAccessor> accessors = new java.util.ArrayList<>(fields.length);
        for (Field field : fields) {
            accessors.add(new FieldAccessor(field));
        }
        return new Fields(accessors);
    }

    /**
     * @deprecated Use {@link #getFields(Class)} instead
     */
    @Deprecated
    public static FieldAccessor[] getFieldAccessors(Object instance) {
        Field[] fields = instance.getClass().getDeclaredFields();
        FieldAccessor[] accessors = new FieldAccessor[fields.length];
        for (int i = 0; i < fields.length; i++) {
            accessors[i] = new FieldAccessor(fields[i]);
        }
        return accessors;
    }

    /**
     * @deprecated Use {@link #getFields(Class)} instead
     * Creates FieldAccessors for all declared fields of a class.
     *
     * @param clazz the class to get fields from
     * @return array of FieldAccessors
     */
    @Deprecated
    public static FieldAccessor[] getFieldAccessors(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        FieldAccessor[] accessors = new FieldAccessor[fields.length];
        for (int i = 0; i < fields.length; i++) {
            accessors[i] = new FieldAccessor(fields[i]);
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
