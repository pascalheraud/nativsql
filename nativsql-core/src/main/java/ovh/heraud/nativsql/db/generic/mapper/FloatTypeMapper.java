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
 * TypeMapper for Float type with flexible numeric conversion.
 * Converts from any numeric SQL type to Float.
 */
public class FloatTypeMapper extends AbstractTypeMapper<Float> {

    @Override
    public Float fromValue(Object value, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) throws ConversionException {
        if (value instanceof Number num)
            return num.floatValue();
        if (value instanceof String str) {
            try {
                return Float.parseFloat(str);
            } catch (NumberFormatException e) {
                throw new ConversionException(Float.class, e);
            }
        }
        if (value instanceof Boolean bool)
            return bool ? 1f : 0f;
        throw new ConversionException(Float.class);
    }

    @Override
    protected Object toDatabaseValue(Float value, Map<ParamKey, Object> params)
            throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
        if (dataType == null) {
            return value;
        }

        try {
            return switch (dataType) {
                case STRING -> value.toString();
                case INTEGER -> Math.round(value);
                case LONG -> (long) Math.round(value);
                case SHORT -> value.shortValue();
                case BYTE -> value.byteValue();
                case FLOAT -> value;
                case DOUBLE -> value.doubleValue();
                case DECIMAL -> new BigDecimal(value);
                case BIG_INTEGER -> BigInteger.valueOf(value.longValue());
                case BOOLEAN -> value != 0;
                default -> throw new ConversionException(dataType.name());
            };
        } catch (NumberFormatException e) {
            throw new ConversionException(dataType.name());
        }
    }
}
