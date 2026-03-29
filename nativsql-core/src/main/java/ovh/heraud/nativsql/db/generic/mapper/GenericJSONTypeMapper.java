package ovh.heraud.nativsql.db.generic.mapper;

import java.sql.ResultSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * Generic TypeMapper for JSON types using Jackson serialization.
 * Handles reading from and writing to database JSON columns across different databases.
 * Works with MySQL, MariaDB, Oracle, and any database that returns JSON as String.
 *
 * @param <T> the Java type to map to/from JSON
 */
public class GenericJSONTypeMapper<T> implements ITypeMapper<T> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Class<T> jsonClass;

    public GenericJSONTypeMapper(Class<T> jsonClass) {
        this.jsonClass = jsonClass;
    }

    @Override
    public T map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            Object dbValue = rs.getObject(columnName);
            if (dbValue == null) {
                return null;
            }

            String jsonStr = null;
            // Handle String JSON (most databases return JSON as String)
            if (dbValue instanceof String) {
                jsonStr = (String) dbValue;
            } else if (dbValue instanceof java.sql.Clob) {
                // Handle CLOB (used by Oracle for JSON)
                java.sql.Clob clob = (java.sql.Clob) dbValue;
                jsonStr = clob.getSubString(1, (int) clob.length());
            } else {
                // Try to convert to string as fallback
                jsonStr = dbValue.toString();
            }

            if (jsonStr == null || jsonStr.isEmpty()) {
                return null;
            }

            return objectMapper.readValue(jsonStr, jsonClass);
        } catch (java.sql.SQLException e) {
            throw new NativSQLException(e);
        } catch (JsonProcessingException e) {
            throw new NativSQLException("Failed to read JSON from column: " + columnName, e);
        }
    }

    @Override
    public Object toDatabase(T value, DbDataType dataType) {
        if (value == null) {
            return null;
        }

        // For IDENTITY type, return as-is
        if (dataType == DbDataType.IDENTITY) {
            return value;
        }

        // JSON types must be converted to JSON, no other conversion is allowed
        if (dataType != null) {
            throw new NativSQLException(
                    "Cannot convert JSON to " + dataType);
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new NativSQLException("Failed to convert to JSON", e);
        }
    }
}
