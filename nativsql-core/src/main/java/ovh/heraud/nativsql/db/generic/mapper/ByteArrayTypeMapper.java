package ovh.heraud.nativsql.db.generic.mapper;

import java.nio.charset.StandardCharsets;
import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;

import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for byte array type.
 * Handles binary data conversion.
 */
public class ByteArrayTypeMapper extends AbstractTypeMapper<byte[]> {

    @Override
    public byte[] fromValue(Object raw, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params)
            throws ConversionException {
        if (raw instanceof byte[] bytes)
            return bytes;
        if (raw instanceof UUID uuid)
            return UUIDTypeMapper.uuidToBytes(uuid);
        if (raw instanceof String str)
            return (str.getBytes());
        throw new ConversionException(byte[].class);
    }

    @Override
    protected Object toDatabaseValue(byte[] value, Map<ParamKey, Object> params)
            throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> new String(value, StandardCharsets.UTF_8);
            case BYTE_ARRAY, IDENTITY -> value;
            case UUID -> UUIDTypeMapper.bytesToUuidString(value);
            default -> throw new ConversionException(dataType.name());
        };
    }
}
