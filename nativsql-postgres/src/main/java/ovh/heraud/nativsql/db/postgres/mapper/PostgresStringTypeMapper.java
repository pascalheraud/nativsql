package ovh.heraud.nativsql.db.postgres.mapper;

import java.util.Map;
import java.util.UUID;

import org.postgresql.util.PGobject;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.db.generic.mapper.StringTypeMapper;
import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * PostgreSQL-specific subclass of {@link StringTypeMapper}.
 * When writing a String value to a UUID column it returns a PGobject
 * with the proper type so the driver knows to cast it to UUID.
 */
public class PostgresStringTypeMapper extends StringTypeMapper {

    @Override
    protected Object toDatabaseValue(String value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        if (dataType == DbDataType.UUID) {
            try {
                UUID.fromString(value); // validate
                PGobject pgObject = new PGobject();
                pgObject.setType("uuid");
                pgObject.setValue(value);
                return pgObject;
            } catch (IllegalArgumentException | java.sql.SQLException e) {
                throw new NativSQLException("Failed to convert String to UUID PGobject", e);
            }
        }
        return super.toDatabaseValue(value, dataType, params);
    }
}
