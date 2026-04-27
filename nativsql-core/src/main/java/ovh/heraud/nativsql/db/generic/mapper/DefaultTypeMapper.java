package ovh.heraud.nativsql.db.generic.mapper;

import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;

import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

public class DefaultTypeMapper<T> extends AbstractTypeMapper<T> {

    @Override
    @SuppressWarnings({ "unchecked" })
    public T fromValue(Object value, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) {
        return (T) value;
    }

    @Override
    protected Object toDatabaseValue(T value, Map<ParamKey, Object> params) {
        return value;
    }
}
