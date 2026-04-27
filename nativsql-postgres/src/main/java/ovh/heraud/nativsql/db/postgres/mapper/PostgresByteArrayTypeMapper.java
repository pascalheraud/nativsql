package ovh.heraud.nativsql.db.postgres.mapper;

import java.util.Map;

import org.postgresql.util.PGobject;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.db.generic.mapper.ByteArrayTypeMapper;
import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * PostgreSQL-specific subclass of {@link ByteArrayTypeMapper}.
 * Adds handling for writing a 16-byte array to a UUID column by wrapping the
 * value in a PGobject of type uuid.
 */
public class PostgresByteArrayTypeMapper extends ByteArrayTypeMapper {

    @Override
    protected Object toDatabaseValue(byte[] value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        if (dataType == DbDataType.UUID) {
            Object obj = super.toDatabaseValue(value, dataType, params);
            if (obj instanceof String str) {
                try {
                    PGobject pg = new PGobject();
                    pg.setType("uuid");
                    pg.setValue(str);
                    return pg;
                } catch (java.sql.SQLException e) {
                    throw new NativSQLException("Failed to wrap UUID string in PGobject", e);
                }
            }
            throw new NativSQLException("Expected String for UUID, got " + (obj == null ? "null" : obj.getClass()));
        }
        return super.toDatabaseValue(value, dataType, params);
    }
}
