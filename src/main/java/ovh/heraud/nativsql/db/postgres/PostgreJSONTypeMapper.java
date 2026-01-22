package ovh.heraud.nativsql.db.postgres;

import java.sql.ResultSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import org.postgresql.util.PGobject;

/**
 * PostgreSQL-specific TypeMapper for JSON/JSONB types.
 * Handles reading from and writing to PostgreSQL JSON/JSONB columns.
 *
 * @param <T> the Java type to map to/from JSON
 */
public class PostgreJSONTypeMapper<T> implements ITypeMapper<T> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Class<T> jsonClass;

    public PostgreJSONTypeMapper(Class<T> jsonClass) {
        this.jsonClass = jsonClass;
    }

    @Override
    public T map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            Object dbValue = rs.getObject(columnName);
            if (dbValue == null) {
                return null;
            }

            // PostgreSQL always returns PGobject for JSON/JSONB columns
            PGobject pgObject = (PGobject) dbValue;

            // Verify it's a JSONB type
            if (pgObject.getType() == null
                    || (!pgObject.getType().equals("jsonb") && !pgObject.getType().equals("json"))) {
                throw new java.sql.SQLException("Expected JSONB type, got: " + pgObject.getType());
            }

            String jsonStr = pgObject.getValue();

            if (jsonStr == null || jsonStr.isEmpty()) {
                return null;
            }

            return objectMapper.readValue(jsonStr, jsonClass);
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("SQLException", e);
        } catch (JsonProcessingException e) {
            throw new NativSQLException("JSONException", e);
        }
    }

    @Override
    public Object toDatabase(T value) {
        if (value == null) {
            return null;
        }
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType("jsonb");
            pgObject.setValue(objectMapper.writeValueAsString(value));
            return pgObject;
        } catch (Exception e) {
            throw new NativSQLException("Failed to convert to JSONB", e);
        }
    }

    @Override
    public String formatParameter(String paramName) {
        // PostgreSQL JSON doesn't need special casting, just return the parameter
        return ":" + paramName;
    }
}
