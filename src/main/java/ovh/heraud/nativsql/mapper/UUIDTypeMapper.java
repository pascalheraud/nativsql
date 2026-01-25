package ovh.heraud.nativsql.mapper;

import java.sql.ResultSet;
import java.util.UUID;

import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Type mapper for java.util.UUID.
 * Handles conversion between UUID and database string representation.
 */
public class UUIDTypeMapper implements ITypeMapper<UUID> {

    @Override
    public UUID map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            String value = rs.getString(columnName);
            if (value == null) {
                return null;
            }
            return UUID.fromString(value);
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to map UUID from column: " + columnName, e);
        }
    }

    @Override
    public Object toDatabase(UUID value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    @Override
    public String formatParameter(String paramName) {
        // Generic format - subclasses can override for database-specific syntax
        return ":" + paramName;
    }
}
