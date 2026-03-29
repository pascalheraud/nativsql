package ovh.heraud.nativsql.util;

import org.jspecify.annotations.NonNull;

/**
 * Represents the details of a MappedBy association (ToOne relationship).
 */
public class MappedByInfo {
    private final @NonNull String foreignKeyProperty;
    private final @NonNull Class<?> repositoryClass;

    /**
     * Creates a new MappedByInfo.
     *
     * @param foreignKeyProperty the property name that contains the foreign key
     * @param repositoryClass the repository class to use
     */
    public MappedByInfo(@NonNull String foreignKeyProperty, @NonNull Class<?> repositoryClass) {
        this.foreignKeyProperty = foreignKeyProperty;
        this.repositoryClass = repositoryClass;
    }

    /**
     * Gets the foreign key property name.
     *
     * @return the property name that contains the foreign key
     */
    @NonNull
    public String getForeignKeyProperty() {
        return foreignKeyProperty;
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
