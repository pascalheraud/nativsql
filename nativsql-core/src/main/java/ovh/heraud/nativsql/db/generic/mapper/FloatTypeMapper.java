package ovh.heraud.nativsql.db.generic.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import org.springframework.jdbc.support.JdbcUtils;

/**
 * TypeMapper for Float type with flexible numeric conversion.
 * Converts from any numeric SQL type to Float.
 */
public class FloatTypeMapper implements ITypeMapper<Float> {
    @Override
    public Float map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            Object value = JdbcUtils.getResultSetValue(rs, index);
            if (value == null)
                return null;

            if (value instanceof Number num) {
                return num.floatValue();
            }
            if (value instanceof String str) {
                return Float.parseFloat(str);
            }
            if (value instanceof Boolean bool) {
                return bool ? 1f : 0f;
            }
            if (value instanceof Float f) {
                return f;
            }
            throw new NativSQLException("Unable to map column " + columnName + " with value " + value + " from class" + value.getClass() + " to Float");
        } catch (RuntimeException | SQLException e) {
            throw new NativSQLException("Unable to map column " + columnName + " to Float", e);
        }
    }

    @Override
    public Object toDatabase(Float value, DbDataType dataType) {
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
            case FLOAT -> value;
            case DOUBLE -> value.doubleValue();
            case DECIMAL -> new java.math.BigDecimal(value);
            case BIG_INTEGER -> java.math.BigInteger.valueOf(value.longValue());
            case BOOLEAN -> value != 0;
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new NativSQLException("Cannot convert Float to " + dataType);
        };
    }
}
