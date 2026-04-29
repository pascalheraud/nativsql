package ovh.heraud.nativsql.db.generic.mapper;

import java.math.BigDecimal;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

public class DefaultTypeMapper<T> extends AbstractTypeMapper<T> {

    public DefaultTypeMapper() {
        super();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected T doMap(Object raw, Map<TypeParamKey, Object> params) {
        return (T) convertValue(raw);
    }

    /**
     * Converts SQL numeric types to Java numeric types intelligently.
     */
    private Object convertValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            if (decimal.scale() <= 0) {
                try {
                    return decimal.longValueExact();
                } catch (ArithmeticException e) {
                    return decimal;
                }
            }
        }
        return value;
    }

    @Override
    protected Object toDatabaseValue(T value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        return value;
    }
}
