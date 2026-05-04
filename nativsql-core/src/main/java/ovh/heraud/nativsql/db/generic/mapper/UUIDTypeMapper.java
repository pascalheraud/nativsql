package ovh.heraud.nativsql.db.generic.mapper;

import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * Type mapper for java.util.UUID.
 * Handles conversion between UUID and database string representation.
 */
public class UUIDTypeMapper extends AbstractTypeMapper<UUID> {

    @Override
    public UUID fromValue(Object value, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params) throws ConversionException {
        if (value == null)
            return null;
        if (value instanceof UUID u)
            return u;
        if (value instanceof String str) {
            try {
                return UUID.fromString(str);
            } catch (IllegalArgumentException e) {
                throw new ConversionException(UUID.class, e);
            }
        }
        if (value instanceof byte[] bytes) {
            if (bytes.length == 16)
                return bytesToUUID(bytes);
            throw new ConversionException(UUID.class);
        }
        throw new ConversionException(UUID.class);
    }

    @Override
    protected Object toDatabaseValue(UUID value, DbDataType dataType, Map<TypeParamKey, Object> params)
            throws ConversionException {
        if (dataType == null) {
            return value.toString();
        }

        return switch (dataType) {
            case STRING -> value.toString();
            case UUID -> value.toString();
            case BYTE_ARRAY -> uuidToBytes(value);
            default -> throw new ConversionException(dataType.name());
        };
    }

    /**
     * Convert a 16-byte array to a {@link UUID}.
     */
    public static UUID bytesToUUID(byte[] bytes) {
        if (bytes.length != 16) {
            throw new NativSQLException("Invalid byte array length for UUID: " + bytes.length + ", expected 16");
        }
        StringBuilder hex = new StringBuilder(32);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        String hexString = hex.toString();
        String uuidString = hexString.substring(0, 8) + "-" +
                hexString.substring(8, 12) + "-" +
                hexString.substring(12, 16) + "-" +
                hexString.substring(16, 20) + "-" +
                hexString.substring(20, 32);
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            throw new NativSQLException("Cannot convert bytes to UUID: invalid format", e);
        }
    }

    /**
     * Convert a {@link UUID} to a 16-byte array.
     */
    public static byte[] uuidToBytes(UUID uuid) {
        String hexString = uuid.toString().replace("-", "");
        byte[] bytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            bytes[i] = (byte) Integer.parseInt(hexString.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    /**
     * Convert a 16-byte array to the canonical dash-separated UUID string.
     */
    public static String bytesToUuidString(byte[] bytes) {
        if (bytes.length != 16) {
            throw new NativSQLException("byte[] length=" + bytes.length + " is not 16 for UUID conversion");
        }
        StringBuilder hex = new StringBuilder(36);
        for (int i = 0; i < bytes.length; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) {
                hex.append('-');
            }
            hex.append(String.format("%02x", bytes[i]));
        }
        return hex.toString();
    }
}
