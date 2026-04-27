package ovh.heraud.nativsql.db.generic.mapper;

import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for Short type with flexible numeric conversion.
 * Converts from any numeric SQL type to Short.
 */
public class ShortTypeMapper extends AbstractTypeMapper<Short> {

    public ShortTypeMapper() {
        super();
    }

    public ShortTypeMapper(Map<TypeParamKey, Object> params) {
        super(params);
    }

    @Override
    public Short fromValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number num) return num.shortValue();
        if (value instanceof String str) {
            try {
                return Short.parseShort(str);
            } catch (NumberFormatException e) {
                throw new NativSQLException("Cannot convert String to Short", e);
            }
        }
        if (value instanceof Boolean bool) return (short) (bool ? 1 : 0);
        throw new NativSQLException("Cannot convert " + value.getClass().getSimpleName() + " to Short");
    }

    @Override
    protected Short doMap(Object raw, Map<TypeParamKey, Object> params) throws NativSQLException {
        return fromValue(raw);
    }

    @Override
    protected Object toDatabaseValue(Short value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> value.toString();
            case INTEGER -> value.intValue();
            case LONG -> value.longValue();
            case SHORT -> value;
            case BYTE -> value.byteValue();
            case FLOAT -> value.floatValue();
            case DOUBLE -> value.doubleValue();
            case DECIMAL -> new java.math.BigDecimal(value);
            case BIG_INTEGER -> java.math.BigInteger.valueOf(value);
            case BOOLEAN -> value != 0;
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new NativSQLException("Cannot convert Short to " + dataType);
        };
    }
}
