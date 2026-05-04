package ovh.heraud.nativsql.db.generic.mapper;

import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for Double type with flexible numeric conversion.
 * Converts from any numeric SQL type to Double.
 */
public class DoubleTypeMapper extends AbstractTypeMapper<Double> {

    @Override
    public Double fromValue(Object value, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params) throws ConversionException {
        if (value == null)
            return null;
        if (value instanceof Number num)
            return num.doubleValue();
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                throw new ConversionException(Double.class, e);
            }
        }
        if (value instanceof Boolean bool)
            return bool ? 1.0d : 0.0d;
        throw new ConversionException(Double.class);
    }

    @Override
    protected Object toDatabaseValue(Double value, DbDataType dataType, Map<TypeParamKey, Object> params)
            throws ConversionException {
        if (dataType == null) {
            return value;
        }

        try {
            return switch (dataType) {
                case STRING -> value.toString();
                case INTEGER -> value.intValue();
                case LONG -> value.longValue();
                case SHORT -> value.shortValue();
                case BYTE -> value.byteValue();
                case FLOAT -> value.floatValue();
                case DOUBLE -> value;
                case DECIMAL -> new java.math.BigDecimal(value);
                case BIG_INTEGER -> java.math.BigInteger.valueOf(value.longValue());
                case BOOLEAN -> value != 0;
                default -> throw new ConversionException(dataType.name());
            };
        } catch (NumberFormatException e) {
            throw new ConversionException(dataType.name());
        }
    }
}
