package ovh.heraud.nativsql.util;

/**
 * Contains information about a composite type extracted from the @CompositeType
 * annotation.
 */
public class CompositeTypeInfo {
    private final String sqlType;

    /**
     * Creates a new CompositeTypeInfo.
     *
     * @param sqlType the database type name for this composite type
     */
    public CompositeTypeInfo(String sqlType) {
        this.sqlType = sqlType;
    }

    /**
     * Gets the database type name.
     *
     * @return the type name
     */
    public String getSqlType() {
        return sqlType;
    }
}
