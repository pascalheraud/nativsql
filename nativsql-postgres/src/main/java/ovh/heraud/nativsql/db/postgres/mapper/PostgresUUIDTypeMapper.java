package ovh.heraud.nativsql.db.postgres.mapper;

import java.util.Map;
import java.util.UUID;

import org.postgresql.util.PGobject;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.db.generic.mapper.UUIDTypeMapper;
import ovh.heraud.nativsql.exception.ConversionException;

/**
 * PostgreSQL-specific UUID mapper that uses ::uuid casting syntax.
 * Inherits from generic UUIDTypeMapper and handles PostgreSQL-specific
 * conversions.
 */
public class PostgresUUIDTypeMapper extends UUIDTypeMapper {

    @Override
    protected Object toDatabaseValue(UUID value, Map<ParamKey, Object> params)
            throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);

        if (dataType == DbDataType.UUID || dataType == DbDataType.IDENTITY || dataType == null) {
            try {
                PGobject pgObject = new PGobject();
                pgObject.setType("uuid");
                pgObject.setValue(value.toString());
                return pgObject;
            } catch (java.sql.SQLException e) {
                throw new ConversionException(UUID.class, e);
            }
        }
        return super.toDatabaseValue(value, params);
    }

    @Override
    public String formatParameter(String paramName, Map<ParamKey, Object> params) {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
        if (dataType == null || dataType == DbDataType.UUID || dataType == DbDataType.IDENTITY) {
            return "(:" + paramName + ")::uuid";
        }
        return ":" + paramName;
    }
}
