package ovh.heraud.nativsql.db.generic.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import org.springframework.jdbc.support.JdbcUtils;

/**
 * TypeMapper for Byte type with flexible numeric conversion.
 * Converts from any numeric SQL type to Byte.
 */
public class ByteTypeMapper implements ITypeMapper<Byte> {
    @Override
    public Byte map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            Object value = JdbcUtils.getResultSetValue(rs, index);
            return fromValue(value);
        } catch (SQLException e) {
            throw new NativSQLException("Unable to map column " + columnName + " to Byte", e);
        }
    }

    @Override
    public Byte fromValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number num) return num.byteValue();
        if (value instanceof String str) {
            try {
                return Byte.parseByte(str);
            } catch (NumberFormatException e) {
                throw new NativSQLException("Cannot convert String '" + str + "' to Byte", e);
            }
        }
        if (value instanceof Boolean bool) return (byte) (bool ? 1 : 0);
        throw new NativSQLException("Cannot convert " + value.getClass().getSimpleName() + " to Byte");
    }

    @Override
    public Object toDatabase(Byte value, DbDataType dataType) {
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
            case BYTE -> value;
            case FLOAT -> value.floatValue();
            case DOUBLE -> value.doubleValue();
            case DECIMAL -> new java.math.BigDecimal(value);
            case BIG_INTEGER -> java.math.BigInteger.valueOf(value);
            case BOOLEAN -> value != 0;
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new NativSQLException("Cannot convert Byte to " + dataType);
        };
    }
}
