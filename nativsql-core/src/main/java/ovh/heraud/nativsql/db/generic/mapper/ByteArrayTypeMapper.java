package ovh.heraud.nativsql.db.generic.mapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for byte array type.
 * Handles binary data conversion.
 */
public class ByteArrayTypeMapper extends AbstractTypeMapper<byte[]> {

    public ByteArrayTypeMapper() {
        super();
    }

    public ByteArrayTypeMapper(Map<TypeParamKey, Object> params) {
        super(params);
    }

    @Override
    protected byte[] doMap(Object raw, Map<TypeParamKey, Object> params) throws NativSQLException {
        if (raw instanceof byte[] bytes) return bytes;
        if (raw instanceof UUID uuid) return UUIDTypeMapper.uuidToBytes(uuid);
        if (raw instanceof String str) return str.getBytes();
        throw new NativSQLException("Cannot convert " + raw.getClass().getSimpleName() + " to byte[]");
    }

    @Override
    protected Object toDatabaseValue(byte[] value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> new String(value, StandardCharsets.UTF_8);
            case BYTE_ARRAY, IDENTITY -> value;
            case UUID -> UUIDTypeMapper.bytesToUuidString(value);
            default -> throw new NativSQLException("Cannot convert byte[] to " + dataType);
        };
    }
}
