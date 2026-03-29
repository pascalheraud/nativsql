package ovh.heraud.nativsql.db.generic.mapper;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.UUID;

import org.springframework.jdbc.support.JdbcUtils;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * TypeMapper for byte array type.
 * Handles binary data conversion.
 */
public class ByteArrayTypeMapper implements ITypeMapper<byte[]> {
    @Override
    public byte[] map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            Object value = JdbcUtils.getResultSetValue(rs, index);
            if (value == null)
                return null;

            if (value instanceof byte[] bytes) {
                return bytes;
            }
            if (value instanceof UUID uuid) {
                return UUIDTypeMapper.uuidToBytes(uuid);
            }
            if (value instanceof String str) {
                return str.getBytes();
            }
            throw new NativSQLException("Unable to map column " + columnName + " with value " + value + " from class "
                    + value.getClass() + " to byte[]");
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to map column: " + columnName, e);
        }
    }

    @Override
    public Object toDatabase(byte[] value, DbDataType dataType) {
        if (value == null) {
            return null;
        }

        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> new String(value, StandardCharsets.UTF_8);
            case BYTE_ARRAY, IDENTITY -> value;
            case UUID -> UUIDTypeMapper.bytesToUuidString(value);
            default -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
        };
    }

}
