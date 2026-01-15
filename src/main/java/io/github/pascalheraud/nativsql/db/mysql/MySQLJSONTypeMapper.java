package io.github.pascalheraud.nativsql.db.mysql;

import java.sql.ResultSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pascalheraud.nativsql.exception.SQLException;
import io.github.pascalheraud.nativsql.mapper.ITypeMapper;

/**
 * MySQL-specific TypeMapper for JSON types.
 * Handles reading from and writing to MySQL JSON columns.
 *
 * @param <T> the Java type to map to/from JSON
 */
public class MySQLJSONTypeMapper<T> implements ITypeMapper<T> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Class<T> jsonClass;

    public MySQLJSONTypeMapper(Class<T> jsonClass) {
        this.jsonClass = jsonClass;
    }

    @Override
    public T map(ResultSet rs, String columnName) throws java.sql.SQLException {
        try {
            Object dbValue = rs.getObject(columnName);
            if (dbValue == null) {
                return null;
            }

            String jsonStr = null;
            // Handle String JSON (MySQL returns JSON as String)
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
            throw e;
        } catch (JsonProcessingException e) {
            throw new java.sql.SQLException("Failed to read JSON from column: " + columnName, e);
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
            throw new SQLException("Failed to convert to JSON", e);
        }
    }

    @Override
    public String formatParameter(String paramName) {
        // MySQL JSON doesn't need special casting, just return the parameter
        return ":" + paramName;
    }
}
