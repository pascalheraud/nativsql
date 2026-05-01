package ovh.heraud.nativsql.mapper;

import java.sql.ResultSet;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
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
     * Maps a value from a ResultSet column using the declared data type and params.
     * Default implementation delegates to {@link #map(ResultSet, String)}, ignoring
     * dataType and params.
     * Overridden in {@code AbstractTypeMapper} to handle the encrypted path when
     * {@code dataType == DbDataType.ENCRYPTED}.
     *
     * @param rs         the ResultSet
     * @param columnName the column name
     * @param dataType   the declared database data type for this field (may be
     *                   null)
     * @param params     the type parameters for this field
     * @return the mapped value
     * @throws NativSQLException if mapping fails
     */
    T map(ResultSet rs, String columnName, @Nullable DbDataType dataType, @Nullable FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params);

    /**
     * Converts a raw value (e.g. from GeneratedKeyHolder) to the target Java type.
     * Default implementation throws UnsupportedOperationException.
     * Override in mappers that need to support this conversion (e.g. numeric types
     * for generated keys).
     *
     * @param value  the raw value to convert
     *               * @param dataType the declared database data type for this
     *               field (may be
     *               null)
     * @param params the type parameters for this field
     * @return the mapped value
     */
    T fromValue(Object value, DbDataType dataType, @Nullable FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params) throws ConversionException;

    /**
     * Convenience wrapper around {@link #fromValue} that catches
     * {@link ConversionException}
     * and rethrows as {@link NativSQLException} with a formatted message.
     * Values are masked as {@code #######} for encrypted fields.
     */
    default T fromValueWithLog(Object value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        try {
            return fromValue(value, dataType, null, params);
        } catch (ConversionException e) {
            boolean encrypted = dataType == DbDataType.ENCRYPTED;
            String valueStr = encrypted ? "#######" : String.valueOf(value);
            throw new NativSQLException("Unable to convert value " + valueStr
                    + " from class " + (value != null ? value.getClass() : "null")
                    + " to " + e.getTargetName(), e);
        }
    }

    /**
     * Converts a Java value to database representation using explicitly provided
     * params.
     * Must be implemented by all mappers.
     *
     * @param value    the Java value to convert
     * @param dataType the declared database data type
     * @param params   the type parameters (KEY, ALGO, PREFIX, …)
     * @return the database representation
     */
    Object toDatabase(T value, DbDataType dataType, Map<TypeParamKey, Object> params);

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
    default String formatParameter(String paramName) {
        return ":" + paramName;
    }
}
