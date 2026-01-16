package io.github.pascalheraud.nativsql.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import io.github.pascalheraud.nativsql.exception.NativSQLException;
import org.springframework.lang.NonNull;

/**
 * Represents the details of a OneToMany association.
 */
public class OneToManyAssociation {
    private final @NonNull String foreignKey;
    private final @NonNull Class<?> repositoryClass;

    /**
     * Creates a new OneToManyAssociation.
     *
     * @param foreignKey the field name in the target entity that references this entity's ID
     * @param repositoryClass the repository class to use
     */
    public OneToManyAssociation(@NonNull  String foreignKey,@NonNull  Class<?> repositoryClass) {
        this.foreignKey = foreignKey;
        this.repositoryClass = repositoryClass;
    }

    /**
     * Gets the foreign key field name.
     *
     * @return the field name in the target entity that references this entity's ID
     */
    @NonNull
    public String getForeignKey() {
        return foreignKey;
    }

    /**
     * Gets the target entity class by extracting it from the repository generic type.
     *
     * @return the target entity class
     * @throws NativSQLException if the entity type cannot be extracted from the repository
     */
    @SuppressWarnings("null")
    @NonNull
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
        throw new NativSQLException("Cannot extract entity type from repository: " + repositoryClass.getName());
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
    @NonNull
    public Class<?> getRepositoryClass() {
        return repositoryClass;
    }
}