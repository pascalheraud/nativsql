package ovh.heraud.nativsql.db.generic.mapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * TypeMapper for Long type with flexible numeric conversion.
 * Converts from any numeric SQL type to Long.
 */
public class LongTypeMapper extends AbstractTypeMapper<Long> {

    @Override
    public Long fromValue(Object value, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params)
            throws ConversionException {
        if (value instanceof Number num)
            return num.longValue();
        if (value instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                throw new ConversionException(Long.class, e);
            }
        }
        if (value instanceof Boolean bool)
            return bool ? 1L : 0L;
        throw new ConversionException(Long.class);
    }

    @Override
    protected Object toDatabaseValue(Long value, Map<ParamKey, Object> params)
            throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> value.toString();
            case INTEGER -> value.intValue();
            case LONG -> value;
            case SHORT -> value.shortValue();
            case BYTE -> value.byteValue();
            case FLOAT -> value.floatValue();
            case DOUBLE -> value.doubleValue();
            case DECIMAL -> new BigDecimal(value);
            case BIG_INTEGER -> BigInteger.valueOf(value);
            case BOOLEAN -> value != 0;
            default -> throw new ConversionException(dataType.name());
        };
    }
}
