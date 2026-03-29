package ovh.heraud.nativsql.db.generic.mapper;

import java.sql.ResultSet;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * Type mapper for java.util.UUID.
 * Handles conversion between UUID and database string representation.
 */
public class UUIDTypeMapper implements ITypeMapper<UUID> {

    @Override
    public UUID map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            Object value = rs.getObject(columnName);
            if (value == null) {
                return null;
            }
            
            if (value instanceof UUID u) {
                return u;
            }
            if (value instanceof byte[] bytes) {
                if (bytes.length == 16) {
                    return bytesToUUID(bytes);
                } else {
                    throw new NativSQLException("Invalid byte array length for UUID: " + bytes.length + ", expected 16");
                }
            } else if (value instanceof String str) {
                try {
                    return UUID.fromString(str);
                } catch (IllegalArgumentException e) {
                    throw new NativSQLException("Invalid UUID string: " + str, e);
                }
            } else {
                throw new NativSQLException("Unexpected type for UUID column: " + value.getClass().getName());
            }
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to map UUID from column: " + columnName, e);
        }
    }

    @Override
    public Object toDatabase(UUID value, DbDataType dataType) {
        if (value == null) {
            return null;
        }

        if (dataType == null) {
            return value.toString();
        }

        return switch (dataType) {
            case STRING -> value.toString();
            case UUID -> value.toString();
            case BYTE_ARRAY -> uuidToBytes(value);
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new NativSQLException("Cannot convert UUID to " + dataType);
        };
    }

    /**
     * Convert a 16-byte array to a {@link UUID} by interpreting the bytes as a
     * hexadecimal representation.
     *
     * @param bytes must be exactly 16 bytes long
     * @return corresponding UUID
     * @throws NativSQLException if the length is incorrect or the resulting
     *                           string is not a valid UUID
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
        // Format as UUID string with dashes: 8-4-4-4-12
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
     * Convert a {@link UUID} to a 16-byte array by interpreting its canonical
     * string representation as hexadecimal digits.
     *
     * @param uuid non-null UUID
     * @return 16-byte array
     */
    public static byte[] uuidToBytes(UUID uuid) {
        String hexString = uuid.toString().replace("-", "");
        byte[] bytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            String hexByte = hexString.substring(i * 2, i * 2 + 2);
            bytes[i] = (byte) Integer.parseInt(hexByte, 16);
        }
        return bytes;
    }

    /**
     * Convert a 16-byte array into the canonical dash‑separated UUID string
     * (8-4-4-4-12). Useful when the database expects a string representation.
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
