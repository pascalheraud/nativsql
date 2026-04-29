package ovh.heraud.nativsql.db.postgres.mapper;

import java.util.Map;
import java.util.UUID;

import org.postgresql.util.PGobject;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.db.generic.mapper.UUIDTypeMapper;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.util.TypeInfo;

/**
 * PostgreSQL-specific UUID mapper that uses ::uuid casting syntax.
 * Inherits from generic UUIDTypeMapper and handles PostgreSQL-specific conversions.
 */
public class PostgresUUIDTypeMapper extends UUIDTypeMapper {

    private final TypeInfo typeInfo;

    public PostgresUUIDTypeMapper(TypeInfo typeInfo) {
        super();
        this.typeInfo = typeInfo;
    }

    @Override
    protected Object toDatabaseValue(UUID value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        if (dataType == DbDataType.UUID || dataType == DbDataType.IDENTITY || dataType == null) {
            try {
                PGobject pgObject = new PGobject();
                pgObject.setType("uuid");
                pgObject.setValue(value.toString());
                return pgObject;
            } catch (java.sql.SQLException e) {
                throw new NativSQLException("Failed to convert UUID to SQL", e);
            }
        }
        return super.toDatabaseValue(value, dataType, params);
    }

    @Override
    public String formatParameter(String paramName) {
        if (typeInfo == null || typeInfo.getDataType() == DbDataType.UUID || typeInfo.getDataType() == DbDataType.IDENTITY) {
            return "(:" + paramName + ")::uuid";
        }
        return ":" + paramName;
    }
}
