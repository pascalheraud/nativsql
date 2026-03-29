package ovh.heraud.nativsql.mapper;

import java.sql.ResultSet;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * A bidirectional type mapper that handles both reading from ResultSet
 * and writing to database parameters.
 *
 * @param <T> the Java type
 */
public interface ITypeMapper<T> {

    /**
     * Maps a value from a ResultSet column.
     *
     * @param rs the ResultSet
     * @param columnName the column name
     * @return the mapped value
     * @throws NativSQLException if mapping fails
     */
    T map(ResultSet rs, String columnName) throws NativSQLException;

    /**
     * Converts a raw value (e.g. from GeneratedKeyHolder) to the target Java type.
     * Default implementation throws UnsupportedOperationException.
     * Override in mappers that need to support this conversion (e.g. numeric types for generated keys).
     *
     * @param value the raw value to convert
     * @return the mapped value
     */
    default T fromValue(Object value) {
        throw new UnsupportedOperationException(
                "fromValue() is not supported by this TypeMapper: " + getClass().getSimpleName());
    }

    /**
     * Converts a Java value to database representation based on a declared DbDataType.
     * Must be implemented by all mappers.
     *
     * @param value the Java value to convert
     * @param dataType the declared database data type
     * @return the database representation
     */
    Object toDatabase(T value, DbDataType dataType);

    /**
     * Formats a parameter for use in SQL, applying any necessary database-specific
     * casting or type conversions.
     * Default implementation returns the parameter with colon prefix (e.g., ":paramName").
     * Override for database-specific formatting (e.g., "(:paramName)::enum_type" for PostgreSQL).
     *
     * @param paramName the parameter name (without the colon prefix)
     * @return the formatted parameter string for SQL (e.g., ":paramName" or "(:paramName)::enum_type")
     */
    default String formatParameter(String paramName) {
        return ":" + paramName;
    }
}
