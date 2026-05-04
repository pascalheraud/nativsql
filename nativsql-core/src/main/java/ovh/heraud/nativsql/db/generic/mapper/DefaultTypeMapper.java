package ovh.heraud.nativsql.db.generic.mapper;

import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

public class DefaultTypeMapper<T> extends AbstractTypeMapper<T> {

    @Override
    @SuppressWarnings("unchecked")
    public T fromValue(Object value, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params) {
        return (T) value;
    }

    @Override
    protected Object toDatabaseValue(T value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        return value;
    }
}
