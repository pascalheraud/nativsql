package ovh.heraud.nativsql.db.generic.mapper;

import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.crypt.CryptConfig;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for Long type with flexible numeric conversion.
 * Converts from any numeric SQL type to Long.
 */
public class LongTypeMapper extends AbstractTypeMapper<Long> {

    public LongTypeMapper() {
        super();
    }

    public LongTypeMapper(Map<TypeParamKey, Object> params, CryptConfig cryptConfig) {
        super(params, cryptConfig);
    }

    @Override
    public Long fromValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number num) return num.longValue();
        if (value instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                throw new NativSQLException("Cannot convert String to Long", e);
            }
        }
        if (value instanceof Boolean bool) return bool ? 1L : 0L;
        throw new NativSQLException("Cannot convert " + value.getClass().getSimpleName() + " to Long");
    }

    @Override
    protected Long doMap(Object raw, @SuppressWarnings("unused") Map<TypeParamKey, Object> params) throws NativSQLException {
        return fromValue(raw);
    }

    @Override
    protected Object toDatabaseValue(Long value, DbDataType dataType, @SuppressWarnings("unused") Map<TypeParamKey, Object> params) {
        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> value.toString();
            case INTEGER -> value.intValue();
            case LONG -> value;
            case SHORT -> value.shortValue();
            case BYTE -> value.byteValue();
            case FLOAT -> value.floatValue();
            case DOUBLE -> value.doubleValue();
            case DECIMAL -> new java.math.BigDecimal(value);
            case BIG_INTEGER -> java.math.BigInteger.valueOf(value);
            case BOOLEAN -> value != 0;
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new NativSQLException("Cannot convert Long to " + dataType);
        };
    }
}
