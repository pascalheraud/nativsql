package ovh.heraud.nativsql.db.postgres.mapper;

import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * PostgreSQL-specific TypeMapper for JSON/JSONB types.
 * Handles reading from and writing to PostgreSQL JSON/JSONB columns.
 *
 * @param <T> the Java type to map to/from JSON
 */
public class PostgreJSONTypeMapper<T> implements ITypeMapper<T> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ConcurrentHashMap<java.lang.reflect.Field, JavaType> TYPE_CACHE = new ConcurrentHashMap<>();

    @Override
    public T map(ResultSet rs, String columnName, DbDataType dataType,
            FieldAccessor<?> fieldAccessor, Map<TypeParamKey, Object> params) throws NativSQLException {
        JavaType javaType = TYPE_CACHE.computeIfAbsent(fieldAccessor.getField(),
                f -> objectMapper.constructType(f.getGenericType()));
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

            return objectMapper.readValue(jsonStr, javaType);
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("SQLException", e);
        } catch (Exception e) {
            throw new NativSQLException("JSONException", e);
        }
    }

    @Override
    public T fromValue(Object value, DbDataType dataType, FieldAccessor<?> fieldAccessor, Map<TypeParamKey, Object> params) {
        JavaType javaType = TYPE_CACHE.computeIfAbsent(fieldAccessor.getField(),
                f -> objectMapper.constructType(f.getGenericType()));
        if (value == null) return null;
        String jsonStr = value instanceof String str ? str : value.toString();
        if (jsonStr.isEmpty()) return null;
        try {
            return objectMapper.readValue(jsonStr, javaType);
        } catch (Exception e) {
            throw new NativSQLException("Failed to parse JSON value", e);
        }
    }

    @Override
    public Object toDatabase(T value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        if (value == null) {
            return null;
        }

        // For IDENTITY type, return as-is
        if (dataType == DbDataType.IDENTITY) {
            return value;
        }
        
        // JSON types must be converted to JSON/JSONB, no other conversion is allowed
        if (dataType != null) {
            throw new NativSQLException(
                    "Cannot convert JSON to " + dataType);
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
}
