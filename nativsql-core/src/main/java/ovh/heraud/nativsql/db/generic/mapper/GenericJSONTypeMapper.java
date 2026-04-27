package ovh.heraud.nativsql.db.generic.mapper;

import java.lang.reflect.Field;
import java.sql.Clob;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
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
    private static final ConcurrentHashMap<Field, JavaType> TYPE_CACHE = new ConcurrentHashMap<>();

    @Override
    public T fromValue(Object raw, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) throws ConversionException {
        JavaType javaType = TYPE_CACHE.computeIfAbsent(fieldAccessor.getField(),
                f -> objectMapper.constructType(f.getGenericType()));
        try {
            String jsonStr = raw instanceof String str ? str
                    : raw instanceof Clob clob ? clob.getSubString(1, (int) clob.length())
                            : raw.toString();
            if (jsonStr.isEmpty())
                throw new ConversionException(javaType.getRawClass());
            return objectMapper.readValue(jsonStr, javaType);
        } catch (Exception e) {
            throw new ConversionException(javaType.getRawClass(), e);
        }
    }

    @Override
    protected Object toDatabaseValue(T value, Map<ParamKey, Object> params)
            throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
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
