package ovh.heraud.nativsql.db.generic.mapper;

import java.sql.ResultSet;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import org.springframework.jdbc.support.JdbcUtils;

/**
 * TypeMapper for Boolean type with flexible conversion.
 * Converts from boolean, numeric (0/1), and string representations.
 */
public class BooleanTypeMapper implements ITypeMapper<Boolean> {
    @Override
    public Boolean map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            Object value = JdbcUtils.getResultSetValue(rs, index);
            if (value == null) return null;

            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof Number num) {
                return num.intValue() != 0;
            }
            if (value instanceof String str) {
                String s = str.toLowerCase().trim();
                if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y") || s.equals("t")) {
                    return true;
                }
                if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("n") || s.equals("f")) {
                    return false;
                }
                // Unknown string representation should be considered an error
                throw new NativSQLException("Cannot convert String '" + str + "' to Boolean");
            }
            throw new NativSQLException("Unable to map column " + columnName + " with value " + value + " from class " + value.getClass() + " to Boolean");
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to map column: " + columnName, e);
        }
    }

    @Override
    public Object toDatabase(Boolean value, DbDataType dataType) {
        if (value == null) {
            return null;
        }

        if (dataType == null) {
            return value;
        }

        return switch (dataType) {            
            case STRING -> value.toString();
            case INTEGER -> value ? 1 : 0;
            case LONG -> value ? 1L : 0L;
            case SHORT -> (short) (value ? 1 : 0);
            case BYTE -> (byte) (value ? 1 : 0);
            case FLOAT -> value ? 1f : 0f;
            case DOUBLE -> value ? 1.0d : 0.0d;
            case DECIMAL -> new java.math.BigDecimal(value ? 1 : 0);
            case BIG_INTEGER -> java.math.BigInteger.valueOf(value ? 1 : 0);
            case BOOLEAN -> value;
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new NativSQLException("Cannot convert Boolean to " + dataType);
        };
    }
}
