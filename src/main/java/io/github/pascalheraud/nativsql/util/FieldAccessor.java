package io.github.pascalheraud.nativsql.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import io.github.pascalheraud.nativsql.exception.SQLException;

/**
 * Wrapper class that provides convenient access to a field on a specific object instance.
 * Combines a Field with its target object for easier manipulation.
 */
public class FieldAccessor {

    private final Field field;
    private final Object instance;

    /**
     * Creates a new FieldAccessor.
     *
     * @param field the field to access
     * @param instance the object instance containing the field
     */
    public FieldAccessor(Field field, Object instance) {
        this.field = field;
        this.instance = instance;
        this.field.setAccessible(true); // Allow access to private fields
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
        return field.getType();
    }

    /**
     * Gets the value of the field on the instance.
     *
     * @return the field value
     * @throws RuntimeException if access fails
     */
    public Object getValue() {
        try {
            return field.get(instance);
        } catch (IllegalAccessException e) {
            throw new SQLException("Failed to get value of field: " + field.getName(), e);
        }
    }

    /**
     * Sets the value of the field on the instance.
     *
     * @param value the value to set
     * @throws RuntimeException if access fails
     */
    public void setValue(Object value) {
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new SQLException("Failed to set value of field: " + field.getName(), e);
        }
    }

    /**
     * Gets an annotation from the field.
     *
     * @param <T> the annotation type
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

    /**
     * Gets the instance object.
     *
     * @return the instance object
     */
    public Object getInstance() {
        return instance;
    }

    @Override
    public String toString() {
        return "FieldAccessor{" +
                "field=" + field.getName() +
                ", type=" + field.getType().getSimpleName() +
                ", instance=" + instance.getClass().getSimpleName() +
                '}';
    }
}
