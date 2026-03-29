package ovh.heraud.nativsql.db.postgres.mapper;

import org.postgresql.util.PGobject;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.db.generic.mapper.ByteArrayTypeMapper;
import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * PostgreSQL-specific subclass of {@link ByteArrayTypeMapper}.
 * Adds handling for writing a 16-byte array to a UUID column by wrapping the
 * value in a PGobject of type uuid.
 */
public class PostgresByteArrayTypeMapper extends ByteArrayTypeMapper {

    @Override
    public Object toDatabase(byte[] value, DbDataType dataType) {
        if (value == null) {
            return null;
        }
        if (dataType == DbDataType.UUID) {
            // let superclass produce the hex string
            Object obj = super.toDatabase(value, dataType);
            if (obj == null) {
                return null;
            }
            if (obj instanceof String str) {
                try {
                    PGobject pg = new PGobject();
                    pg.setType("uuid");
                    pg.setValue(str);
                    return pg;
                } catch (java.sql.SQLException e) {
                    throw new NativSQLException("Failed to wrap UUID string in PGobject", e);
                }
            } else {
                throw new NativSQLException("Expected superclass to convert byte[] to String for UUID, got " + obj.getClass());
            }
        }
        return super.toDatabase(value, dataType);
    }
}