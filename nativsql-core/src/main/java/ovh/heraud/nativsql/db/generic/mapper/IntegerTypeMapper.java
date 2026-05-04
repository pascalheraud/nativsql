package ovh.heraud.nativsql.db.generic.mapper;

import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for Integer type with flexible numeric conversion.
 * Converts from any numeric SQL type to Integer.
 */
public class IntegerTypeMapper extends AbstractTypeMapper<Integer> {

    @Override
    public Integer fromValue(Object value, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params) throws ConversionException {
        if (value == null)
            return null;
        if (value instanceof Number num)
            return num.intValue();
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                throw new ConversionException(Integer.class, e);
            }
        }
        if (value instanceof Boolean bool)
            return bool ? 1 : 0;
        throw new ConversionException(Integer.class);
    }

    @Override
    protected Object toDatabaseValue(Integer value, DbDataType dataType,
            Map<TypeParamKey, Object> params) throws ConversionException {
        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> value.toString();
            case INTEGER -> value;
            case LONG -> value.longValue();
            case SHORT -> value.shortValue();
            case BYTE -> value.byteValue();
            case FLOAT -> value.floatValue();
            case DOUBLE -> value.doubleValue();
            case DECIMAL -> new java.math.BigDecimal(value);
            case BIG_INTEGER -> java.math.BigInteger.valueOf(value);
            case BOOLEAN -> value != 0;
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new ConversionException(dataType.name());
        };
    }
}
