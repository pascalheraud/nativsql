package ovh.heraud.nativsql.db.generic.mapper;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.crypt.CryptConfig;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * Generic TypeMapper for JSON types using Jackson serialization.
 * Handles reading from and writing to database JSON columns across different databases.
 * Works with MySQL, MariaDB, Oracle, and any database that returns JSON as String.
 *
 * @param <T> the Java type to map to/from JSON
 */
public class GenericJSONTypeMapper<T> extends AbstractTypeMapper<T> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Class<T> jsonClass;

    public GenericJSONTypeMapper(Class<T> jsonClass) {
        super();
        this.jsonClass = jsonClass;
    }

    public GenericJSONTypeMapper(Class<T> jsonClass, Map<TypeParamKey, Object> params, CryptConfig cryptConfig) {
        super(params, cryptConfig);
        this.jsonClass = jsonClass;
    }

    @Override
    protected T doMap(Object raw, Map<TypeParamKey, Object> params) throws NativSQLException {
        String jsonStr;
        if (raw instanceof String str) {
            jsonStr = str;
        } else if (raw instanceof java.sql.Clob clob) {
            try {
                jsonStr = clob.getSubString(1, (int) clob.length());
            } catch (java.sql.SQLException e) {
                throw new NativSQLException("Failed to read CLOB value", e);
            }
        } else {
            jsonStr = raw.toString();
        }

        if (jsonStr.isEmpty()) return null;

        try {
            return objectMapper.readValue(jsonStr, jsonClass);
        } catch (JsonProcessingException e) {
            throw new NativSQLException("Failed to parse JSON value", e);
        }
    }

    @Override
    protected Object toDatabaseValue(T value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        if (dataType == DbDataType.IDENTITY) {
            return value;
        }
        if (dataType != null) {
            throw new NativSQLException("Cannot convert JSON to " + dataType);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new NativSQLException("Failed to convert to JSON", e);
        }
    }
}
