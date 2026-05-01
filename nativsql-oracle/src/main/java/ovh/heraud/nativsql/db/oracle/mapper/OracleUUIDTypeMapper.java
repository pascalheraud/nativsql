package ovh.heraud.nativsql.db.oracle.mapper;

import java.util.Map;
import java.util.UUID;

import oracle.sql.BLOB;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.db.generic.mapper.UUIDTypeMapper;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * Oracle-specific UUID type mapper that handles oracle.sql.BLOB types.
 */
@SuppressWarnings("deprecation")
public class OracleUUIDTypeMapper extends UUIDTypeMapper {

    @Override
    public UUID fromValue(Object raw, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params)
            throws NativSQLException, ConversionException {
        if (raw instanceof BLOB blob) {
            byte[] bytes = extractBytesFromBlob(blob);
            if (bytes.length == 16) {
                return bytesToUUID(bytes);
            }
            throw new NativSQLException("Invalid byte array length for UUID: " + bytes.length + ", expected 16");
        }
        return super.fromValue(raw, dataType, fieldAccessor, params);
    }

    private byte[] extractBytesFromBlob(BLOB blob) throws NativSQLException {
        try {
            int length = (int) blob.length();
            return blob.getBytes(1, length);
        } catch (Exception e) {
            throw new NativSQLException("Failed to extract bytes from BLOB", e);
        }
    }
}
