package ovh.heraud.nativsql.db.generic.mapper;

import java.math.BigDecimal;
import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;

import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for BigDecimal type with flexible numeric conversion.
 * Converts from any numeric SQL type to BigDecimal.
 */
public class BigDecimalTypeMapper extends AbstractTypeMapper<BigDecimal> {

    @Override
    public BigDecimal fromValue(Object value, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) throws ConversionException {
        if (value instanceof BigDecimal decimal)
            return decimal;
        if (value instanceof Number num)
            return BigDecimal.valueOf(num.doubleValue());
        if (value instanceof Boolean bool)
            return BigDecimal.valueOf(bool ? 1 : 0);
        if (value instanceof String str) {
            try {
                return new BigDecimal(str);
            } catch (NumberFormatException e) {
                throw new ConversionException(BigDecimal.class, e);
            }
        }
        throw new ConversionException(BigDecimal.class);
    }

    @Override
    protected Object toDatabaseValue(BigDecimal value, Map<ParamKey, Object> params)
            throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);

        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> value.toPlainString();
            case INTEGER -> value.intValue();
            case LONG -> value.longValue();
            case SHORT -> value.shortValue();
            case BYTE -> value.byteValue();
            case FLOAT -> value.floatValue();
            case DOUBLE -> value.doubleValue();
            case DECIMAL -> value;
            case BIG_INTEGER -> value.toBigInteger();
            case BOOLEAN -> value.compareTo(BigDecimal.ZERO) != 0;
            default -> throw new ConversionException(dataType.name());
        };
    }
}
