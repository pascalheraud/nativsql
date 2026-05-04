package ovh.heraud.nativsql.db.generic.mapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * Generic TypeMapper for JSON types using Jackson serialization.
 * Handles reading from and writing to database JSON columns across different
 * databases.
 * Works with MySQL, MariaDB, Oracle, and any database that returns JSON as
 * String.
 *
 * @param <T> the Java type to map to/from JSON
 */
public class GenericJSONTypeMapper<T> extends AbstractTypeMapper<T> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ConcurrentHashMap<java.lang.reflect.Field, JavaType> TYPE_CACHE = new ConcurrentHashMap<>();

    @Override
    public T fromValue(Object raw, DbDataType dataType, @Nullable FieldAccessor<?> fieldAccessor, Map<TypeParamKey, Object> params) throws ConversionException {
        JavaType javaType = TYPE_CACHE.computeIfAbsent(fieldAccessor.getField(),
                f -> objectMapper.constructType(f.getGenericType()));
        try {
            String jsonStr = raw instanceof String str ? str
                    : raw instanceof java.sql.Clob clob ? clob.getSubString(1, (int) clob.length())
                    : raw.toString();
            if (jsonStr.isEmpty())
                return null;
            return objectMapper.readValue(jsonStr, javaType);
        } catch (Exception e) {
            throw new ConversionException(javaType.getRawClass(), e);
        }
    }

    @Override
    protected Object toDatabaseValue(T value, DbDataType dataType, Map<TypeParamKey, Object> params)
            throws ConversionException {
        if (dataType == null || dataType == DbDataType.IDENTITY) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new ConversionException(String.class, e);
            }
        }
        throw new ConversionException(dataType.name());
    }
}
