package ovh.heraud.nativsql.db.postgres.mapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.postgresql.util.PGobject;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * PostgreSQL-specific TypeMapper for JSON/JSONB types.
 * Handles reading from and writing to PostgreSQL JSON/JSONB columns.
 *
 * @param <T> the Java type to map to/from JSON
 */
public class PostgreJSONTypeMapper<T> extends AbstractTypeMapper<T> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ConcurrentHashMap<java.lang.reflect.Field, JavaType> TYPE_CACHE = new ConcurrentHashMap<>();

    @Override
 public T fromValue(Object value, FieldAccessor<?> fieldAccessor,
 Map<ParamKey, Object> params) {
        JavaType javaType = TYPE_CACHE.computeIfAbsent(fieldAccessor.getField(),
                f -> objectMapper.constructType(f.getGenericType()));
        String jsonStr = value instanceof PGobject pg ? pg.getValue()
                : value instanceof String str ? str : value.toString();
        if (jsonStr.isEmpty())
            throw new NativSQLException("Empty JSON value cannot be converted to " + javaType.getRawClass());
        try {
            return objectMapper.readValue(jsonStr, javaType);
        } catch (Exception e) {
            throw new NativSQLException("Failed to parse JSON value", e);
        }
    }

    @Override
 protected Object toDatabaseValue(T value, Map<ParamKey, Object> params) {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);

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
