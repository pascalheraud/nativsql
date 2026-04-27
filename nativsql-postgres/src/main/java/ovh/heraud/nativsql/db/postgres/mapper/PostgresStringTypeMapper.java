package ovh.heraud.nativsql.db.postgres.mapper;

import java.util.Map;
import java.util.UUID;

import org.postgresql.util.PGobject;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.db.generic.mapper.StringTypeMapper;
import ovh.heraud.nativsql.exception.ConversionException;

/**
 * PostgreSQL-specific subclass of {@link StringTypeMapper}.
 * When writing a String value to a UUID column it returns a PGobject
 * with the proper type so the driver knows to cast it to UUID.
 */
public class PostgresStringTypeMapper extends StringTypeMapper {

    @Override
 protected Object toDatabaseValue(String value, Map<ParamKey, Object> params)
 throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);

        if (dataType == DbDataType.UUID) {
            try {
                UUID.fromString(value);
                PGobject pgObject = new PGobject();
                pgObject.setType("uuid");
                pgObject.setValue(value);
                return pgObject;
            } catch (IllegalArgumentException | java.sql.SQLException e) {
                throw new ConversionException(UUID.class, e);
            }
        }
        return super.toDatabaseValue(value, params);
    }
}
