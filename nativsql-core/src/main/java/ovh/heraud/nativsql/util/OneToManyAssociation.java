package ovh.heraud.nativsql.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Represents the details of a OneToMany association.
 */
public class OneToManyAssociation {
    private final String foreignKey;
    private final Class<?> repositoryClass;

    /**
     * Creates a new OneToManyAssociation.
     *
     * @param foreignKey      the field name in the target entity that references
     *                        this entity's ID
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
     * Gets the target entity class by extracting it from the repository generic
     * type.
     *
     * @return the target entity class
     * @throws NativSQLException if the entity type cannot be extracted from the
     *                           repository
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
                        Class<?> tp = (Class<?>) typeArguments[0];
                        if (tp != null) {
                            return tp;
                        }
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
   public Class<?> getRepositoryClass() {
        return repositoryClass;
    }
}