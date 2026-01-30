package ovh.heraud.nativsql.db;

/**
 * Converts between Java identifiers (camelCase) and database identifiers (snake_case).
 *
 * Allows different databases to use different naming conventions for columns and fields.
 */
public interface IdentifierConverter {

    /**
     * Convert a Java identifier (camelCase) to a database identifier.
     *
     * @param javaIdentifier the Java field name in camelCase
     * @return the database column name
     */
    String toDB(String javaIdentifier);

    /**
     * Convert a database identifier to a Java identifier (camelCase).
     *
     * @param dbIdentifier the database column name
     * @return the Java field name in camelCase
     */
    String fromDB(String dbIdentifier);
}
