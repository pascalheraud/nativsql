package io.github.pascalheraud.nativsql.mapper;

import java.sql.ResultSet;

import io.github.pascalheraud.nativsql.exception.NativSQLException;

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
     * Converts a Java value to database representation for storage.
     *
     * @param value the Java value to convert
     * @return the database representation
     */
    Object toDatabase(T value);

    /**
     * Formats a parameter for use in SQL, applying any necessary database-specific
     * casting or type conversions.
     *
     * @param paramName the parameter name (without the colon prefix)
     * @return the formatted parameter string for SQL (e.g., ":paramName" or "(:paramName)::enum_type")
     */
    String formatParameter(String paramName);
}
