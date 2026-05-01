package ovh.heraud.nativsql.db.generic.mapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for BigInteger type with flexible numeric conversion.
 * Converts from any numeric SQL type to BigInteger.
 */
public class BigIntegerTypeMapper extends AbstractTypeMapper<BigInteger> {

    @Override
    public BigInteger fromValue(Object value, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params) throws ConversionException {
        if (value == null)
            return null;
        if (value instanceof BigInteger bigInt)
            return bigInt;
        if (value instanceof BigDecimal decimal)
            return decimal.toBigInteger();
        if (value instanceof Number num)
            return BigInteger.valueOf(num.longValue());
        if (value instanceof Boolean bool)
            return BigInteger.valueOf(bool ? 1 : 0);
        if (value instanceof String str) {
            try {
                return new BigInteger(str);
            } catch (NumberFormatException e) {
                throw new ConversionException(BigInteger.class, e);
            }
        }
        throw new ConversionException(BigInteger.class);
    }

    @Override
    protected Object toDatabaseValue(BigInteger value, DbDataType dataType, Map<TypeParamKey, Object> params)
            throws ConversionException {
        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> value.toString();
            case INTEGER -> value.intValue();
            case LONG -> value.longValue();
            case SHORT -> value.shortValue();
            case BYTE -> value.byteValue();
            case FLOAT -> value.floatValue();
            case DOUBLE -> value.doubleValue();
            case DECIMAL -> new BigDecimal(value);
            case BIG_INTEGER -> value;
            case BOOLEAN -> !value.equals(BigInteger.ZERO);
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new ConversionException(dataType.name());
        };
    }
}
