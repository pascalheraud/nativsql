package ovh.heraud.nativsql.util;

import ovh.heraud.nativsql.db.IdentifierConverter;

/**
 * Interface for building SQL statements with identifier conversion.
 * Implementations are responsible for constructing SQL strings using the provided
 * identifier converter to transform Java identifiers (camelCase) to database identifiers (snake_case).
 */
public interface SQLBuilder {

    /**
     * Builds a SQL statement using the provided identifier converter.
     * Appends the SQL to the provided StringBuilder.
     *
     * @param sb                  the StringBuilder to append the SQL to
     * @param identifierConverter the converter to use for identifier transformation
     */
    void build(StringBuilder sb, IdentifierConverter identifierConverter);
}
