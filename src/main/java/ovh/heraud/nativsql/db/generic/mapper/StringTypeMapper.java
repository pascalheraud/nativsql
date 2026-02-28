package ovh.heraud.nativsql.db.generic.mapper;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import org.springframework.jdbc.support.JdbcUtils;

/**
 * TypeMapper for String type with flexible conversion.
 * Converts from various types to String representation.
 */
public class StringTypeMapper implements ITypeMapper<String> {
    @Override
    public String map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            Object value = JdbcUtils.getResultSetValue(rs, index);
            if (value == null)
                return null;

            if (value instanceof String str) {
                return str;
            }
            if (value instanceof java.math.BigDecimal bd) {
                return bd.stripTrailingZeros().toPlainString();
            }
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            if (value instanceof UUID uuid) {
                return uuid.toString();
            }
            if (value instanceof LocalDate ld) {
                return ld.toString();
            }
            if (value instanceof LocalDateTime ldt) {
                return ldt.toString();
            }
            if (value instanceof java.sql.Timestamp ts) {
                return ts.toLocalDateTime().toString();
            }
            if (value instanceof java.sql.Date sqlDate) {
                return sqlDate.toString();
            }
            if (value instanceof byte[] bytes) {
                return Arrays.toString(bytes);
            }
            throw new NativSQLException("Unable to map column " + columnName + " with value " + value + " from class "
                    + value.getClass() + " to String");
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to map column: " + columnName, e);
        }
    }

    @Override
    public Object toDatabase(String value, DbDataType dataType) {
        if (value == null) {
            return null;
        }

        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> value;
            case INTEGER -> {
                try {
                    yield Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String '" + value + "' to Integer", e);
                }
            }
            case LONG -> {
                try {
                    yield Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String '" + value + "' to Long", e);
                }
            }
            case SHORT -> {
                try {
                    yield Short.parseShort(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String '" + value + "' to Short", e);
                }
            }
            case BYTE -> {
                try {
                    yield Byte.parseByte(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String '" + value + "' to Byte", e);
                }
            }
            case FLOAT -> {
                try {
                    yield Float.parseFloat(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String '" + value + "' to Float", e);
                }
            }
            case DOUBLE -> {
                try {
                    yield Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String '" + value + "' to Double", e);
                }
            }
            case DECIMAL -> {
                try {
                    yield new java.math.BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String '" + value + "' to BigDecimal", e);
                }
            }
            case BIG_INTEGER -> {
                try {
                    yield new java.math.BigInteger(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String '" + value + "' to BigInteger", e);
                }
            }
            case BOOLEAN -> Boolean.parseBoolean(value) || value.equals("1") || value.equalsIgnoreCase("yes")
                    || value.equalsIgnoreCase("true");
            case UUID -> {
                // For MySQL schema we store UUID as CHAR(36) so keep sending the
                // string representation rather than a UUID object which could be
                // serialized by the driver and corrupt the DB value.
                try {
                    UUID.fromString(value); // validate format
                    yield value;
                } catch (IllegalArgumentException e) {
                    throw new NativSQLException("Invalid UUID format: '" + value + "'", e);
                }
            }
            case BYTE_ARRAY -> value.getBytes();
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new NativSQLException("Cannot convert String to " + dataType);
        };
    }
}
