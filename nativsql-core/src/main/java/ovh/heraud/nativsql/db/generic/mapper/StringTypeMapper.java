package ovh.heraud.nativsql.db.generic.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for String type with flexible conversion.
 * Converts from various types to String representation.
 */
public class StringTypeMapper extends AbstractTypeMapper<String> {

    @Override
    public String fromValue(Object raw, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params)
            throws ConversionException {
        if (raw == null)
            return null;
        if (raw instanceof String str)
            return str;
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
        throw new ConversionException(String.class);
    }

    @Override
    protected Object toDatabaseValue(String value, DbDataType dataType, Map<TypeParamKey, Object> params)
            throws ConversionException {
        if (dataType == null) {
            return value;
        }

        try {
            return switch (dataType) {
                case STRING -> value;
                case INTEGER -> Integer.parseInt(value);
                case LONG -> Long.parseLong(value);
                case SHORT -> Short.parseShort(value);
                case BYTE -> Byte.parseByte(value);
                case FLOAT -> Float.parseFloat(value);
                case DOUBLE -> Double.parseDouble(value);
                case DECIMAL -> new java.math.BigDecimal(value);
                case BIG_INTEGER -> new java.math.BigInteger(value);
                case BOOLEAN -> Boolean.parseBoolean(value) || value.equals("1")
                        || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true");
                case UUID -> {
                    UUID.fromString(value);
                    yield value;
                }
                case BYTE_ARRAY -> value.getBytes();
                default -> throw new ConversionException(dataType.name());
            };
        } catch (IllegalArgumentException e) {
            throw new ConversionException(dataType.name());
        }
    }
}
