package ovh.heraud.nativsql.util;

import java.util.Arrays;
import java.util.stream.Collectors;

import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Utility class for SQL-related operations.
 */
public final class SqlUtils {
    private SqlUtils() {}

    /**
     * Converts an array of column names (camelCase) to a comma-separated list in snake_case.
     * @param identifierConverter the identifier converter for column name transformation
     * @param columns the property names (camelCase) to convert
     * @return comma-separated list in snake_case
     * @throws NativSQLException if columns is null or empty
     */
    public static String getColumnsList(IdentifierConverter identifierConverter, String... columns) {
        if (columns == null || columns.length == 0) {
            throw new NativSQLException("At least one column must be specified");
        }

        return Arrays.stream(columns)
                .map(identifierConverter::toDB)
                .collect(Collectors.joining(", "));
    }
}
