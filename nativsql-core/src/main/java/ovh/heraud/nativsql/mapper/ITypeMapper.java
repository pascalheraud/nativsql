package ovh.heraud.nativsql.mapper;

import java.sql.ResultSet;
import java.util.Map;

import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * A bidirectional type mapper that handles both reading from ResultSet
 * and writing to database parameters.
 *
 * @param <T> the Java type
 */
public interface ITypeMapper<T> {

        /**
         * Maps a value from a ResultSet column using params.
         * Overridden in {@code AbstractTypeMapper} to handle the encrypted path when
         * {@code params} contains {@code TypeParamKey.ENCRYPTED}.
         *
         * @param rs         the ResultSet
         * @param columnName the column name
         * @param params     the type parameters for this field
         * @return the mapped value
         * @throws NativSQLException if mapping fails
         */

        T map(ResultSet rs, String columnName, FieldAccessor<?> fieldAccessor,
                        Map<ParamKey, Object> params);

        /**
         * Maps a raw value (already read from a ResultSet or supplied directly) to the
         * target Java type, applying decryption when {@code params} contains
         * {@code TypeParamKey.ENCRYPTED}.
         *
         * @param description   an optional label used in error messages (typically the
         *                      column name)
         * @param fieldAccessor the field accessor, used for contextual error messages
         *                      (nullable)
         * @param params        the type parameters for this field
         * @param value         the raw value to convert (null returns null)
         * @return the mapped value, or null if {@code value} is null
         */

        T map(String description, FieldAccessor<?> fieldAccessor,
                        Map<ParamKey, Object> params, Object value);

        /**
         * Converts a Java value to database representation using explicitly provided
         * params.
         * Must be implemented by all mappers.
         *
         * @param value  the Java value to convert
         * @param params the type parameters (KEY, ALGO, PREFIX, …)
         * @return the database representation
         */

        Object toDatabase(T value, Map<ParamKey, Object> params);

        /**
         * Formats a parameter for use in SQL, applying any necessary database-specific
         * casting or type conversions.
         * Default implementation returns the parameter with colon prefix (e.g.,
         * ":paramName").
         * Override for database-specific formatting (e.g., "(:paramName)::enum_type"
         * for PostgreSQL).
         *
         * @param paramName the parameter name (without the colon prefix)
         * @return the formatted parameter string for SQL (e.g., ":paramName" or
         *         "(:paramName)::enum_type")
         */
        String formatParameter(String paramName, Map<ParamKey, Object> params);
}
