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
        return Arrays.stream(columns)
                .map(identifierConverter::toDB)
                .collect(Collectors.joining(", "));
    }

    /**
     * Converts a dot-notation column path to a camelCase parameter name.
     * Plain column names (no dot) are returned unchanged.
     * "assoc.column" → "assocColumn".
     * Nested paths (more than one dot) are rejected with {@link NativSQLException}.
     *
     * @param column the column name or dot-notation path
     * @return the camelCase parameter name
     * @throws NativSQLException if the path has more than one dot or an empty column segment
     */
    public static String columnPathToParamName(String column) {
        if (!column.contains(".")) return column;
        String[] parts = column.split("\\.", -1);
        if (parts.length != 2) {
            throw new NativSQLException(
                "Nested column paths are not supported: '" + column
                + "'. Only one level of join is allowed (e.g. 'assoc.column')");
        }
        String col = parts[1];
        if (col.isEmpty()) {
            throw new NativSQLException(
                "Invalid column path '" + column + "': column name cannot be empty");
        }
        return parts[0] + Character.toUpperCase(col.charAt(0)) + col.substring(1);
    }
}
