package ovh.heraud.nativsql.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.jdbc.support.JdbcUtils;
import ovh.heraud.nativsql.util.FieldAccessor;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.crypt.EncryptionUtils;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Abstract superclass for all NativSQL type mappers.
 * Stateless with respect to encryption — no crypt state is held in the
 * instance.
 * The encrypt/decrypt path is driven entirely by the {@code params} passed at
 * call time and delegated to {@link EncryptionUtils}.
 *
 * <p>
 * Concrete mappers implement {@link #fromValue(Object, FieldAccessor, Map)} and
 * {@link #toDatabaseValue(Object, Map)}.
 *
 * @param <T> the Java type this mapper handles
 */

public abstract class AbstractTypeMapper<T> implements ITypeMapper<T> {
    // ---- map path ----

    @Override

    public final T map(ResultSet rs, String columnName,
            FieldAccessor<?> fieldAccessor, Map<ParamKey, Object> params) throws NativSQLException {
        Integer index = null;
        Object raw = null;
        try {
            index = rs.findColumn(columnName);
            raw = JdbcUtils.getResultSetValue(rs, index);
        } catch (SQLException e) {
            throw new NativSQLException("Unable to map column " + columnName + "index(" + index + ")", e);
        }
        return map(columnName, fieldAccessor, params, raw);
    }

    @Override

    public final T map(String description, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params,
            Object value) {
        if (value == null)
            return null;
        boolean encrypted = params.containsKey(TypeParamKey.ENCRYPTED);
        if (encrypted) {
            String decrypted = EncryptionUtils.decrypt(value, description, params);
            return fromValueWithLog(description, null, decrypted, fieldAccessor, params);
        }
        return fromValueWithLog(description, null, value, fieldAccessor, params);
    }

    private T fromValueWithLog(String columnName, Integer index, Object value,
            FieldAccessor<?> fieldAccessor, Map<ParamKey, Object> params) {
        try {
            return fromValue(value, fieldAccessor, params);
        } catch (ConversionException e) {
            boolean encrypted = params.containsKey(TypeParamKey.ENCRYPTED);
            String valueStr = encrypted ? "#######" : String.valueOf(value);
            throw new NativSQLException("Unable to map column " + columnName + " (index " + index + ")"
                    + " with value " + valueStr
                    + " from class " + (value != null ? value.getClass() : "null")
                    + " to " + e.getTargetName(), e);
        }
    }

    @Override
    public String formatParameter(String paramName, Map<ParamKey, Object> params) {
        return ":" + paramName;
    }

    // ---- toDatabase path ----

    public final Map<ParamKey, Object> enrichParamsWithDbDataType(Map<ParamKey, Object> params,
            DbDataType dataType) {
        Map<ParamKey, Object> enriched = new java.util.HashMap<>(params);
        enriched.put(TypeParamKey.DB_DATA_TYPE, dataType);
        return enriched;
    }

    @Override

    public final Object toDatabase(T value, Map<ParamKey, Object> params) {
        if (value == null)
            return null;
        boolean encrypted = params.containsKey(TypeParamKey.ENCRYPTED);
        try {
            if (encrypted) {
                String serialized = (String) toDatabaseValue(value,
                        enrichParamsWithDbDataType(params, DbDataType.STRING));
                return EncryptionUtils.encrypt(serialized, params);
            }
            return toDatabaseValue(value, params);
        } catch (ConversionException e) {
            String valueStr = encrypted ? "#######" : String.valueOf(value);
            throw new NativSQLException("Unable to convert value " + valueStr
                    + " from class " + value.getClass()
                    + " to " + e.getTargetName(), e);
        }
    }

    // ---- abstract hooks ----

    /**
     * Converts a raw database value to the target Java type.
     *
     * <p>
     * Called after optional decryption — {@code value} is always the plain-text
     * representation at this point (never the ciphertext).
     * Null values are handled upstream and never passed here.
     *
     * @param value         the raw (or decrypted) value from the database
     * @param fieldAccessor the field accessor, for context
     * @param params        the type parameters for this field
     * @return the converted Java value
     * @throws ConversionException if the value cannot be converted
     */
    protected abstract T fromValue(Object value, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) throws ConversionException;

    /**
     * Converts a Java value to its database representation.
     *
     * <p>
     * For encrypted fields this is always called with
     * {@code DB_DATA_TYPE = STRING} so the result can be passed to
     * {@link EncryptionUtils#encrypt}. Null values are handled upstream and
     * never passed here.
     *
     * @param value  the Java value to serialise
     * @param params the type parameters for this field
     * @return the database-ready value
     * @throws ConversionException if the value cannot be serialised
     */
    protected abstract Object toDatabaseValue(T value, Map<ParamKey, Object> params)
            throws ConversionException;
}
