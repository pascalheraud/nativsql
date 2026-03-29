package ovh.heraud.nativsql.db.postgres.mapper;

import java.util.UUID;

import org.postgresql.util.PGobject;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.db.generic.mapper.StringTypeMapper;

/**
 * PostgreSQL-specific subclass of {@link StringTypeMapper}.
 * When writing a String value to a UUID column it returns a PGobject
 * with the proper type so the driver knows to cast it to UUID.
 */
public class PostgresStringTypeMapper extends StringTypeMapper {

    @Override
    public Object toDatabase(String value, DbDataType dataType) {
        if (value == null) {
            return null;
        }
        if (dataType == DbDataType.UUID) {
            try {
                // validate
                UUID.fromString(value);
                PGobject pgObject = new PGobject();
                pgObject.setType("uuid");
                pgObject.setValue(value);
                return pgObject;
            } catch (IllegalArgumentException | java.sql.SQLException e) {
                throw new NativSQLException("Failed to convert String to UUID PGobject: " + value, e);
            }
        }
        return super.toDatabase(value, dataType);
    }
}