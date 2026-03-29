package ovh.heraud.nativsql.util;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
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
     * Includes inherited fields from superclasses.
     * Provides fast lookup by field name via a map.
     *
     * @param clazz the class to get fields from
     * @return Fields wrapper with all field accessors
     */
    public static Fields getFields(Class<?> clazz) {
        List<FieldAccessor<?>> accessors = new java.util.ArrayList<>();

        // Collect fields from the class hierarchy (including superclasses)
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            Field[] declaredFields = current.getDeclaredFields();
            for (Field field : declaredFields) {
                accessors.add(new FieldAccessor<>(field));
            }
            current = current.getSuperclass();
        }

        return new Fields(accessors);
    }

    /**
     * @deprecated Use {@link #getFields(Class)} instead
     */
    @Deprecated
    public static FieldAccessor<?>[] getFieldAccessors(Object instance) {
        List<FieldAccessor<?>> list = getFields(instance.getClass()).list();
        return list.toArray(new FieldAccessor<?>[0]);
    }

    /**
     * @deprecated Use {@link #getFields(Class)} instead
     * Creates FieldAccessors for all declared fields of a class.
     * Includes inherited fields from superclasses.
     *
     * @param clazz the class to get fields from
     * @return array of FieldAccessors
     */
    @Deprecated
    public static FieldAccessor<?>[] getFieldAccessors(Class<?> clazz) {
        List<FieldAccessor<?>> accessors = new java.util.ArrayList<>();

        // Collect fields from the class hierarchy (including superclasses)
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            Field[] declaredFields = current.getDeclaredFields();
            for (Field field : declaredFields) {
                accessors.add(new FieldAccessor<>(field));
            }
            current = current.getSuperclass();
        }

        return accessors.toArray(new FieldAccessor<?>[0]);
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

    /**
     * Extract the method name from a getter method reference.
     * Uses SerializedLambda to inspect the underlying method.
     *
     * IMPORTANT: The method reference MUST be assigned to a variable for this to work.
     * This is a limitation of Java's SerializedLambda mechanism.
     *
     * Example:
     *   var email = User::getEmail;  // OK - assigned to variable
     *   method(User::getEmail);       // NOT OK - inline method reference won't work
     *
     * Best practice: Define constants in a companion class:
     *   public class UserColumns {
     *       public static final Getter<User> EMAIL = User::getEmail;
     *       public static final Getter<User> ID = User::getId;
     *   }
     *
     * @param getter the getter method reference (must be assigned to a variable)
     * @return the method name (e.g., "getEmail", "getId", "isActive")
     * @throws NativSQLException if the method name cannot be extracted
     */
    public static <T> String extractMethodName(Getter<T> getter) {
        try {
            Method writeReplace = getter.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object serializedLambda = writeReplace.invoke(getter);

            if (serializedLambda instanceof SerializedLambda lambda) {
                return lambda.getImplMethodName();
            }
            throw new NativSQLException("Could not extract method name from getter");
        } catch (Exception e) {
            throw new NativSQLException("Failed to extract method name from getter. " +
                    "Make sure the getter is assigned to a variable (e.g., var email = User::getEmail;)", e);
        }
    }

    /**
     * Convert Java getter method name to database column name.
     * Handles both "get" and "is" prefixes.
     *
     * Examples:
     *   getEmail -> email
     *   getId -> id
     *   isActive -> active
     *
     * @param methodName the getter method name
     * @return the database column name
     */
    public static String convertToColumnName(String methodName) {
        if (methodName.startsWith("get")) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        } else if (methodName.startsWith("is")) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return methodName;
    }

    /**
     * Get the database column name from a getter method reference.
     * Combines extractMethodName and convertToColumnName in one call.
     *
     * Examples:
     *   User::getEmail -> "email"
     *   User::getId -> "id"
     *   User::isActive -> "active"
     *
     * @param getter the getter method reference
     * @return the database column name
     * @throws NativSQLException if the column name cannot be determined
     */
    public static <T> String getColumnName(Getter<T> getter) {
        String methodName = extractMethodName(getter);
        return convertToColumnName(methodName);
    }

    /**
     * Get database column names from multiple getter method references.
     * Converts each getter reference to its corresponding column name.
     *
     * Examples:
     *   User::getEmail, User::getId -> ["email", "id"]
     *   User::getFirstName, User::getLastName -> ["firstName", "lastName"]
     *
     * @param getters the getter method references
     * @return an array of database column names
     * @throws NativSQLException if any column name cannot be determined
     */
    @SafeVarargs
    public static <T> String[] getColumnNames(Getter<T>... getters) {
        String[] columns = new String[getters.length];
        for (int i = 0; i < getters.length; i++) {
            columns[i] = getColumnName(getters[i]);
        }
        return columns;
    }

    /**
     * Functional interface for getter method references.
     * Allows passing method references like User::getId, User::getEmail, etc.
     *
     * @param <T> the type of the object containing the getter
     */
    @FunctionalInterface
    public interface Getter<T> extends Serializable {
        Object get(T obj);
    }
}
