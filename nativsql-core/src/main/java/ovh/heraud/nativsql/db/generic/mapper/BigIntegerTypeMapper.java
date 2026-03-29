package ovh.heraud.nativsql.db.generic.mapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import org.springframework.jdbc.support.JdbcUtils;

/**
 * TypeMapper for BigInteger type with flexible numeric conversion.
 * Converts from any numeric SQL type to BigInteger.
 */
public class BigIntegerTypeMapper implements ITypeMapper<BigInteger> {
    @Override
    public BigInteger map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            Object value = JdbcUtils.getResultSetValue(rs, index);
            if (value == null)
                return null;

            if (value instanceof BigInteger bigInt) {
                return bigInt;
            }
            if (value instanceof BigDecimal decimal) {
                return decimal.toBigInteger();
            }
            if (value instanceof Number num) {
                return BigInteger.valueOf(num.longValue());
            }
            if (value instanceof Boolean bool) {
                return BigInteger.valueOf(bool ? 1 : 0);
            }
            if (value instanceof String str) {
                return new BigInteger(str);
            }
            throw new NativSQLException("Unable to map column " + columnName + " with value " + value + " from class"
                    + value.getClass() + " to BigInteger");
        } catch (RuntimeException | SQLException e) {
            throw new NativSQLException("Unable to map column " + columnName + " to BigInteger", e);
        }
    }

    @Override
    public Object toDatabase(BigInteger value, DbDataType dataType) {
        if (value == null) {
            return null;
        }

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
            default -> throw new NativSQLException("Cannot convert BigInteger to " + dataType);
        };
    }
}
