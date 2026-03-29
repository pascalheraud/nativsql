package ovh.heraud.nativsql.util;

/**
 * Contains information about a composite type extracted from the @CompositeType annotation.
 */
public class CompositeTypeInfo {
    private final String typeName;

    /**
     * Creates a new CompositeTypeInfo.
     *
     * @param typeName the database type name for this composite type
     */
    public CompositeTypeInfo(String typeName) {
        this.typeName = typeName;
    }

    /**
     * Gets the database type name.
     *
     * @return the type name
     */
    public String getTypeName() {
        return typeName;
    }
}
