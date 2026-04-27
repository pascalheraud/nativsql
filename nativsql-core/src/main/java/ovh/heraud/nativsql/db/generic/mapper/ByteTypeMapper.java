package ovh.heraud.nativsql.db.generic.mapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import ovh.heraud.nativsql.util.FieldAccessor;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;

import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for Byte type with flexible numeric conversion.
 * Converts from any numeric SQL type to Byte.
 */
public class ByteTypeMapper extends AbstractTypeMapper<Byte> {

    @Override
    public Byte fromValue(Object value, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) throws ConversionException {
        if (value instanceof Number num)
            return (num.byteValue());
        if (value instanceof String str) {
            try {
                return (Byte.parseByte(str));
            } catch (NumberFormatException e) {
                throw new ConversionException(Byte.class, e);
            }
        }
        if (value instanceof Boolean bool)
            return ((byte) (bool ? 1 : 0));
        throw new ConversionException(Byte.class);
    }

    @Override
    protected Object toDatabaseValue(Byte value, Map<ParamKey, Object> params)
            throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> value.toString();
            case INTEGER -> value.intValue();
            case LONG -> value.longValue();
            case SHORT -> value.shortValue();
            case BYTE -> value;
            case FLOAT -> value.floatValue();
            case DOUBLE -> value.doubleValue();
            case DECIMAL -> new BigDecimal(value);
            case BIG_INTEGER -> BigInteger.valueOf(value);
            case BOOLEAN -> value != 0;
            case IDENTITY -> throw new ConversionException(dataType.name());
            default -> throw new ConversionException(dataType.name());
        };
    }
}
