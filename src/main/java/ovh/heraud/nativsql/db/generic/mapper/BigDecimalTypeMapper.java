package ovh.heraud.nativsql.db.generic.mapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.support.JdbcUtils;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * TypeMapper for BigDecimal type with flexible numeric conversion.
 * Converts from any numeric SQL type to BigDecimal.
 */
public class BigDecimalTypeMapper implements ITypeMapper<BigDecimal> {
    @Override
    public BigDecimal map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            Object value = JdbcUtils.getResultSetValue(rs, index);
            if (value == null)
                return null;

            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof Number num) {
                return BigDecimal.valueOf(num.doubleValue());
            }
            if (value instanceof Boolean bool) {
                return BigDecimal.valueOf(bool ? 1 : 0);
            }
            if (value instanceof String str) {
                return new BigDecimal(str);
            }
            throw new NativSQLException("Unable to map column " + columnName + " with value " + value + " from class"
                    + value.getClass() + " to BigDecimal");
        } catch (RuntimeException | SQLException e) {
            throw new NativSQLException("Unable to map column " + columnName + " to BigDecimal", e);
        }
    }

    @Override
    public Object toDatabase(BigDecimal value, DbDataType dataType) {
        if (value == null) {
            return null;
        }

        // If no specific type is declared, return as-is
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
