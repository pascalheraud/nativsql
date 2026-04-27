package ovh.heraud.nativsql.db.generic.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for String type with flexible conversion.
 * Converts from various types to String representation.
 */
public class StringTypeMapper extends AbstractTypeMapper<String> {

    public StringTypeMapper() {
        super();
    }

    public StringTypeMapper(Map<TypeParamKey, Object> params) {
        super(params);
    }

    @Override
    public String fromValue(Object value) {
        if (value == null) return null;
        if (value instanceof String str) return str;
        return value.toString();
    }

    @Override
    protected String doMap(Object raw, @SuppressWarnings("unused") Map<TypeParamKey, Object> params) throws NativSQLException {
        if (raw instanceof String str) {
            return str;
        }
        if (raw instanceof java.math.BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        if (raw instanceof Number || raw instanceof Boolean) {
            return raw.toString();
        }
        if (raw instanceof UUID uuid) {
            return uuid.toString();
        }
        if (raw instanceof LocalDate ld) {
            return ld.toString();
        }
        if (raw instanceof LocalDateTime ldt) {
            return ldt.toString();
        }
        if (raw instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (raw instanceof java.sql.Date sqlDate) {
            return sqlDate.toString();
        }
        if (raw instanceof byte[] bytes) {
            return Arrays.toString(bytes);
        }
        throw new NativSQLException("Unable to map value from class " + raw.getClass() + " to String");
    }

    @Override
    protected Object toDatabaseValue(String value, DbDataType dataType, @SuppressWarnings("unused") Map<TypeParamKey, Object> params) {
        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> value;
            case INTEGER -> {
                try {
                    yield Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String to Integer", e);
                }
            }
            case LONG -> {
                try {
                    yield Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String to Long", e);
                }
            }
            case SHORT -> {
                try {
                    yield Short.parseShort(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String to Short", e);
                }
            }
            case BYTE -> {
                try {
                    yield Byte.parseByte(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String to Byte", e);
                }
            }
            case FLOAT -> {
                try {
                    yield Float.parseFloat(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String to Float", e);
                }
            }
            case DOUBLE -> {
                try {
                    yield Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String to Double", e);
                }
            }
            case DECIMAL -> {
                try {
                    yield new java.math.BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String to BigDecimal", e);
                }
            }
            case BIG_INTEGER -> {
                try {
                    yield new java.math.BigInteger(value);
                } catch (NumberFormatException e) {
                    throw new NativSQLException("Cannot convert String to BigInteger", e);
                }
            }
            case BOOLEAN -> Boolean.parseBoolean(value) || value.equals("1") || value.equalsIgnoreCase("yes")
                    || value.equalsIgnoreCase("true");
            case UUID -> {
                try {
                    UUID.fromString(value); // validate format
                    yield value;
                } catch (IllegalArgumentException e) {
                    throw new NativSQLException("Invalid UUID format", e);
                }
            }
            case BYTE_ARRAY -> value.getBytes();
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new NativSQLException("Cannot convert String to " + dataType);
        };
    }
}
