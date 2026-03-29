package ovh.heraud.nativsql.db.generic.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import org.springframework.jdbc.support.JdbcUtils;

/**
 * TypeMapper for Long type with flexible numeric conversion.
 * Converts from any numeric SQL type to Long.
 */
public class LongTypeMapper implements ITypeMapper<Long> {
    @Override
    public Long map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            Object value = JdbcUtils.getResultSetValue(rs, index);
            return fromValue(value);
        } catch (SQLException e) {
            throw new NativSQLException("Unable to map column " + columnName + " to Long", e);
        }
    }

    @Override
    public Long fromValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number num) return num.longValue();
        if (value instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                throw new NativSQLException("Cannot convert String '" + str + "' to Long", e);
            }
        }
        if (value instanceof Boolean bool) return bool ? 1L : 0L;
        throw new NativSQLException("Cannot convert " + value.getClass().getSimpleName() + " to Long");
    }

    @Override
    public Object toDatabase(Long value, DbDataType dataType) {
        if (value == null) {
            return null;
        }

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
