package ovh.heraud.nativsql.db.generic.mapper;

import java.math.BigDecimal;
import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for BigDecimal type with flexible numeric conversion.
 * Converts from any numeric SQL type to BigDecimal.
 */
public class BigDecimalTypeMapper extends AbstractTypeMapper<BigDecimal> {

    @Override
    public BigDecimal fromValue(Object value, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params) throws ConversionException {
        if (value == null)
            return null;
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
    protected Object toDatabaseValue(BigDecimal value, DbDataType dataType, Map<TypeParamKey, Object> params)
            throws ConversionException {
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
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new ConversionException(dataType.name());
        };
    }
}
