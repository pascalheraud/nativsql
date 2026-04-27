package ovh.heraud.nativsql.util;


/**
 * Represents the details of a MappedBy association (ToOne relationship).
 */
public class MappedByInfo {
    private final String foreignKeyProperty;
    private final Class<?> repositoryClass;

    /**
     * Creates a new MappedByInfo.
     *
     * @param foreignKeyProperty the property name that contains the foreign key
     * @param repositoryClass    the repository class to use
     */
    public MappedByInfo(String foreignKeyProperty, Class<?> repositoryClass) {
        this.foreignKeyProperty = foreignKeyProperty;
        this.repositoryClass = repositoryClass;
    }

    /**
     * Gets the foreign key property name.
     *
     * @return the property name that contains the foreign key
     */
   public String getForeignKeyProperty() {
        return foreignKeyProperty;
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
