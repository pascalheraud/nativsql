package ovh.heraud.nativsql.db.generic.mapper;

import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for Integer type with flexible numeric conversion.
 * Converts from any numeric SQL type to Integer.
 */
public class IntegerTypeMapper extends AbstractTypeMapper<Integer> {

    public IntegerTypeMapper() {
        super();
    }

    public IntegerTypeMapper(Map<TypeParamKey, Object> params) {
        super(params);
    }

    @Override
    public Integer fromValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number num) return num.intValue();
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                throw new NativSQLException("Cannot convert String to Integer", e);
            }
        }
        if (value instanceof Boolean bool) return bool ? 1 : 0;
        throw new NativSQLException("Cannot convert " + value.getClass().getSimpleName() + " to Integer");
    }

    @Override
    protected Integer doMap(Object raw, @SuppressWarnings("unused") Map<TypeParamKey, Object> params) throws NativSQLException {
        return fromValue(raw);
    }

    @Override
    protected Object toDatabaseValue(Integer value, DbDataType dataType, @SuppressWarnings("unused") Map<TypeParamKey, Object> params) {
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
            default -> throw new NativSQLException("Cannot convert Integer to " + dataType);
        };
    }
}
