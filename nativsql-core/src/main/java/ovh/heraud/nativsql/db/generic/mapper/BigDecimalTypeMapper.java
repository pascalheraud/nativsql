package ovh.heraud.nativsql.db.generic.mapper;

import java.math.BigDecimal;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for BigDecimal type with flexible numeric conversion.
 * Converts from any numeric SQL type to BigDecimal.
 */
public class BigDecimalTypeMapper extends AbstractTypeMapper<BigDecimal> {

    public BigDecimalTypeMapper() {
        super();
    }

    public BigDecimalTypeMapper(Map<TypeParamKey, Object> params) {
        super(params);
    }

    @Override
    public BigDecimal fromValue(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
        if (value instanceof Boolean bool) return BigDecimal.valueOf(bool ? 1 : 0);
        if (value instanceof String str) {
            try {
                return new BigDecimal(str);
            } catch (NumberFormatException e) {
                throw new NativSQLException("Cannot convert String to BigDecimal", e);
            }
        }
        throw new NativSQLException("Cannot convert " + value.getClass().getSimpleName() + " to BigDecimal");
    }

    @Override
    protected BigDecimal doMap(Object raw, Map<TypeParamKey, Object> params) throws NativSQLException {
        return fromValue(raw);
    }

    @Override
    protected Object toDatabaseValue(BigDecimal value, DbDataType dataType, Map<TypeParamKey, Object> params) {
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
            default -> throw new NativSQLException("Cannot convert BigDecimal to " + dataType);
        };
    }
}
