package ovh.heraud.nativsql.db.mariadb;

import java.sql.ResultSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * MariaDB-specific TypeMapper for JSON types.
 * Handles reading from and writing to MariaDB JSON columns.
 *
 * @param <T> the Java type to map to/from JSON
 */
public class MariaDBJSONTypeMapper<T> implements ITypeMapper<T> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Class<T> jsonClass;

    public MariaDBJSONTypeMapper(Class<T> jsonClass) {
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
            // Handle String JSON (MariaDB returns JSON as String)
            if (dbValue instanceof String) {
                jsonStr = (String) dbValue;
            } else {
                // Try to convert to string
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
    public Object toDatabase(T value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new NativSQLException("Failed to convert to JSON", e);
        }
    }

    @Override
    public String formatParameter(String paramName) {
        // MariaDB JSON doesn't need special casting, just return the parameter
        return ":" + paramName;
    }
}
