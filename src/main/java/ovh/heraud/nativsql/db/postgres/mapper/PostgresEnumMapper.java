package ovh.heraud.nativsql.db.postgres.mapper;

import java.sql.ResultSet;

import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;

/**
 * PostgreSQL-specific mapper for enum types that handles both reading from
 * database
 * and writing to database with proper type casting.
 *
 * @param <E> the enum type
 */
@RequiredArgsConstructor
public class PostgresEnumMapper<E extends Enum<E>> implements ITypeMapper<E> {

    private final Class<E> enumClass;
    private final String dbTypeName;

    @Override
    public E map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            Object dbValue = rs.getObject(columnName);
            if (dbValue == null) {
                return null;
            }

            // Handle String representation of enum from database
            if (dbValue instanceof String) {
                return Enum.valueOf(enumClass, (String) dbValue);
            }

            throw new NativSQLException("Cannot parse enum from value: " + dbValue);

        } catch (IllegalArgumentException e) {
            throw new NativSQLException(
                    "Invalid enum value for " + enumClass.getSimpleName() + ": " + e.getMessage(), e);
        } catch (java.sql.SQLException e) {
            throw new NativSQLException(e);
        }
    }

    @Override
    public Object toDatabase(E value) {
        if (value == null) {
            return null;
        }
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType(dbTypeName);
            pgObject.setValue(value.name());
            return pgObject;
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to convert enum to SQL", e);
        }
    }

    @Override
    public String formatParameter(String paramName) {
        return "(:" + paramName + ")::" + dbTypeName;
    }
}
