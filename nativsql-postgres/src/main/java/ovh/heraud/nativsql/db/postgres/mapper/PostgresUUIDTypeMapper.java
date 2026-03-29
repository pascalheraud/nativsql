package ovh.heraud.nativsql.db.postgres.mapper;

import java.util.UUID;

import org.postgresql.util.PGobject;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.db.generic.mapper.UUIDTypeMapper;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.util.TypeInfo;

/**
 * PostgreSQL-specific UUID mapper that uses ::uuid casting syntax.
 * Inherits from generic UUIDTypeMapper and handles PostgreSQL-specific conversions.
 */
public class PostgresUUIDTypeMapper extends UUIDTypeMapper {

    private TypeInfo typeInfo;

    public PostgresUUIDTypeMapper(TypeInfo typeInfo) {
        this.typeInfo = typeInfo;
    }

    @Override
    public Object toDatabase(UUID value, DbDataType dataType) {
        if (value == null) {
            return null;
        }

        if (dataType== DbDataType.UUID || dataType == DbDataType.IDENTITY || dataType == null) {
            try {
                PGobject pgObject = new PGobject();
                pgObject.setType("uuid");
                pgObject.setValue(value.toString());
                return pgObject;
            } catch (java.sql.SQLException e) {
                throw new NativSQLException("Failed to convert UUID to SQL", e);
            }
        }

        // Fall back to parent implementation for other conversions
        return super.toDatabase(value, dataType);
    }

    @Override
    public String formatParameter(String paramName) {
        if (typeInfo == null || typeInfo.getDataType() == DbDataType.UUID || typeInfo.getDataType() == DbDataType.IDENTITY) {
            // PostgreSQL needs explicit ::uuid casting for type safety
            return "(:" + paramName + ")::uuid";
        }
        return ":" + paramName;
    }
}
