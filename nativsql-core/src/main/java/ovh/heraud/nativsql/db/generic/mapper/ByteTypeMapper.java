package ovh.heraud.nativsql.db.generic.mapper;

import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for Byte type with flexible numeric conversion.
 * Converts from any numeric SQL type to Byte.
 */
public class ByteTypeMapper extends AbstractTypeMapper<Byte> {

    public ByteTypeMapper() {
        super();
    }

    public ByteTypeMapper(Map<TypeParamKey, Object> params) {
        super(params);
    }

    @Override
    public Byte fromValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number num) return num.byteValue();
        if (value instanceof String str) {
            try {
                return Byte.parseByte(str);
            } catch (NumberFormatException e) {
                throw new NativSQLException("Cannot convert String to Byte", e);
            }
        }
        if (value instanceof Boolean bool) return (byte) (bool ? 1 : 0);
        throw new NativSQLException("Cannot convert " + value.getClass().getSimpleName() + " to Byte");
    }

    @Override
    protected Byte doMap(Object raw, Map<TypeParamKey, Object> params) throws NativSQLException {
        return fromValue(raw);
    }

    @Override
    protected Object toDatabaseValue(Byte value, DbDataType dataType, Map<TypeParamKey, Object> params) {
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
            case DECIMAL -> new java.math.BigDecimal(value);
            case BIG_INTEGER -> java.math.BigInteger.valueOf(value);
            case BOOLEAN -> value != 0;
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new NativSQLException("Cannot convert Byte to " + dataType);
        };
    }
}
