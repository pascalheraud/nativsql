package ovh.heraud.nativsql.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Wrapper class that provides convenient access to a field.
 * Operates on any object instance passed to its methods.
 */
public class FieldAccessor {

    private Field field;
    private Class<?> fieldType;

    /**
     * Creates a new FieldAccessor.
     *
     * @param field the field to access
     */
    public FieldAccessor(Field field) {
        this.field = field;
        this.field.setAccessible(true); // Allow access to private fields
    }

    public FieldAccessor(Class<?> clazz) {
        this.fieldType = clazz;
    }

    /**
     * Gets the field name.
     *
     * @return the field name
     */
    public String getName() {
        return field.getName();
    }

    /**
     * Gets the field type.
     *
     * @return the field type
     */
    public Class<?> getType() {
        return field == null ? this.fieldType : field.getType();
    }

    /**
     * Gets the value of the field on the instance.
     *
     * @param instance the object instance to get the value from
     * @return the field value
     * @throws RuntimeException if access fails
     */
    @SuppressWarnings("unchecked")
    public <T> T getValue(Object instance) {
        try {
            return (T) field.get(instance);
        } catch (IllegalAccessException e) {
            throw new NativSQLException("Failed to get value of field: " + field.getName(), e);
        }
    }

    /**
     * Sets the value of the field on the instance.
     *
     * @param instance the object instance to set the value on
     * @param value    the value to set
     * @throws RuntimeException if access fails
     */
    public void setValue(Object instance, Object value) {
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new NativSQLException("Failed to set value of field: " + field.getName(), e);
        }
    }

    /**
     * Gets an annotation from the field.
     *
     * @param <T>             the annotation type
     * @param annotationClass the annotation class
     * @return the annotation if present, null otherwise
     */
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return field.getAnnotation(annotationClass);
    }

    /**
     * Checks if the field has a specific annotation.
     *
     * @param annotationClass the annotation class
     * @return true if the annotation is present, false otherwise
     */
    public boolean hasAnnotation(Class<? extends Annotation> annotationClass) {
        return field.isAnnotationPresent(annotationClass);
    }

    /**
     * Gets the underlying Field object.
     *
     * @return the Field object
     */
    public Field getField() {
        return field;
    }

    @Override
    public String toString() {
        return "FieldAccessor{" +
                "field=" + field.getName() +
                ", type=" + field.getType().getSimpleName() +
                '}';
    }
}
