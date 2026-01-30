package ovh.heraud.nativsql.db.postgres.mapper;

import java.sql.ResultSet;
import java.util.UUID;

import org.postgresql.util.PGobject;

import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * PostgreSQL-specific UUID mapper that uses ::uuid casting syntax.
 * Handles both reading from and writing to PostgreSQL UUID columns.
 */
public class PostgresUUIDTypeMapper implements ovh.heraud.nativsql.mapper.ITypeMapper<UUID> {

    @Override
    public UUID map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            Object value = rs.getObject(columnName);
            if (value == null) {
                return null;
            }
            if (value instanceof UUID) {
                return (UUID) value;
            }
            if (value instanceof String) {
                return UUID.fromString((String) value);
            }
            throw new NativSQLException("Cannot parse UUID from value: " + value);
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to map UUID from column: " + columnName, e);
        }
    }

    @Override
    public Object toDatabase(UUID value) {
        if (value == null) {
            return null;
        }
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType("uuid");
            pgObject.setValue(value.toString());
            return pgObject;
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to convert UUID to SQL", e);
        }
    }

    @Override
    public String formatParameter(String paramName) {
        // PostgreSQL needs explicit ::uuid casting for type safety
        return "(:" + paramName + ")::uuid";
    }
}
