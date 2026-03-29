package ovh.heraud.nativsql.db.oracle.mapper;

import java.sql.ResultSet;
import java.util.UUID;

import oracle.sql.BLOB;
import ovh.heraud.nativsql.db.generic.mapper.UUIDTypeMapper;
import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Oracle-specific UUID type mapper that handles oracle.sql.BLOB types.
 */
@SuppressWarnings("deprecation")
public class OracleUUIDTypeMapper extends UUIDTypeMapper {

    @Override
    public UUID map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            Object value = rs.getObject(columnName);
            if (value == null) {
                return null;
            }

            // Handle oracle.sql.BLOB
            if (value instanceof BLOB blob) {
                byte[] bytes = extractBytesFromBlob(blob);
                if (bytes.length == 16) {
                    return bytesToUUID(bytes);
                } else {
                    throw new NativSQLException("Invalid byte array length for UUID: " + bytes.length + ", expected 16");
                }
            }

            // Fall back to generic mapper for other types
            return super.map(rs, columnName);
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to map UUID from column: " + columnName, e);
        }
    }

    /**
     * Extract bytes from oracle.sql.BLOB.
     */
    private byte[] extractBytesFromBlob(BLOB blob) throws NativSQLException {
        try {
            int length = (int) blob.length();
            return blob.getBytes(1, length);
        } catch (Exception e) {
            throw new NativSQLException("Failed to extract bytes from BLOB", e);
        }
    }
}
