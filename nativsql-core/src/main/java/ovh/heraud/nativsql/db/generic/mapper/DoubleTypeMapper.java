package ovh.heraud.nativsql.db.generic.mapper;

import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for Double type with flexible numeric conversion.
 * Converts from any numeric SQL type to Double.
 */
public class DoubleTypeMapper extends AbstractTypeMapper<Double> {

    public DoubleTypeMapper() {
        super();
    }

    public DoubleTypeMapper(Map<TypeParamKey, Object> params) {
        super(params);
    }

    @Override
    public Double fromValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number num) return num.doubleValue();
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                throw new NativSQLException("Cannot convert String to Double", e);
            }
        }
        if (value instanceof Boolean bool) return bool ? 1.0d : 0.0d;
        throw new NativSQLException("Cannot convert " + value.getClass().getSimpleName() + " to Double");
    }

    @Override
    protected Double doMap(Object raw, Map<TypeParamKey, Object> params) throws NativSQLException {
        return fromValue(raw);
    }

    @Override
    protected Object toDatabaseValue(Double value, DbDataType dataType, Map<TypeParamKey, Object> params) {
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
            case DOUBLE -> value;
            case DECIMAL -> new java.math.BigDecimal(value);
            case BIG_INTEGER -> java.math.BigInteger.valueOf(value.longValue());
            case BOOLEAN -> value != 0;
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new NativSQLException("Cannot convert Double to " + dataType);
        };
    }
}
