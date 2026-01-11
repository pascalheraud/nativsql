package io.github.pascalheraud.nativsql.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import jakarta.annotation.Nonnull;

import io.github.pascalheraud.nativsql.annotation.OneToMany;
import io.github.pascalheraud.nativsql.exception.SQLException;

/**
 * Wrapper class that provides convenient access to a field.
 * Operates on any object instance passed to its methods.
 */
public class FieldAccessor {

    private final Field field;

    /**
     * Creates a new FieldAccessor.
     *
     * @param field the field to access
     */
    public FieldAccessor(Field field) {
        this.field = field;
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
     * @param instance the object instance to get the value from
     * @return the field value
     * @throws RuntimeException if access fails
     */
    @SuppressWarnings("unchecked")
    public <T> T getValue(Object instance) {
        try {
            return (T)field.get(instance);
        } catch (IllegalAccessException e) {
            throw new SQLException("Failed to get value of field: " + field.getName(), e);
        }
    }

    /**
     * Sets the value of the field on the instance.
     *
     * @param instance the object instance to set the value on
     * @param value the value to set
     * @throws RuntimeException if access fails
     */
    public void setValue(Object instance, Object value) {
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
     * Gets the OneToMany association details.
     *
     * @return a non-null OneToManyAssociation object
     * @throws SQLException if the @OneToMany annotation is not present on this field
     */
    @Nonnull
    public OneToManyAssociation getOneToMany() {
        OneToMany annotation = field.getAnnotation(OneToMany.class);
        if (annotation == null) {
            throw new SQLException("Field is not annotated with @OneToMany: " + field.getName());
        }
        return new OneToManyAssociation(annotation.mappedBy(), annotation.repository());
    }

    /**
     * Represents the details of a OneToMany association.
     */
    public static class OneToManyAssociation {
        private final String foreignKey;
        private final Class<?> repositoryClass;

        /**
         * Creates a new OneToManyAssociation.
         *
         * @param foreignKey the field name in the target entity that references this entity's ID
         * @param repositoryClass the repository class to use
         */
        public OneToManyAssociation(String foreignKey, Class<?> repositoryClass) {
            this.foreignKey = foreignKey;
            this.repositoryClass = repositoryClass;
        }

        /**
         * Gets the foreign key field name.
         *
         * @return the field name in the target entity that references this entity's ID
         */
        public String getForeignKey() {
            return foreignKey;
        }

        /**
         * Gets the target entity class by extracting it from the repository generic type.
         *
         * @return the target entity class
         * @throws SQLException if the entity type cannot be extracted from the repository
         */
        public Class<?> getEntity() {
            Type[] genericInterfaces = repositoryClass.getGenericInterfaces();
            for (Type genericInterface : genericInterfaces) {
                if (genericInterface instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
                    Type rawType = parameterizedType.getRawType();
                    // Check if it's GenericRepository or a subclass
                    if (rawType instanceof Class<?> && isGenericRepositoryClass((Class<?>) rawType)) {
                        Type[] typeArguments = parameterizedType.getActualTypeArguments();
                        if (typeArguments.length > 0 && typeArguments[0] instanceof Class<?>) {
                            return (Class<?>) typeArguments[0];
                        }
                    }
                }
            }
            throw new SQLException("Cannot extract entity type from repository: " + repositoryClass.getName());
        }

        /**
         * Checks if a class is GenericRepository or extends it.
         */
        private boolean isGenericRepositoryClass(Class<?> clazz) {
            if (clazz.getName().contains("GenericRepository")) {
                return true;
            }
            // Check superclass
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                return isGenericRepositoryClass(superclass);
            }
            return false;
        }

        /**
         * Gets the repository class.
         *
         * @return the repository class
         */
        public Class<?> getRepositoryClass() {
            return repositoryClass;
        }
    }

    @Override
    public String toString() {
        return "FieldAccessor{" +
                "field=" + field.getName() +
                ", type=" + field.getType().getSimpleName() +
                '}';
    }
}
