package ovh.heraud.nativsql.db.postgres.mapper;

import java.sql.ResultSet;
import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
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
    public E map(ResultSet rs, String columnName, DbDataType dataType,
            FieldAccessor<?> fieldAccessor, Map<TypeParamKey, Object> params)
            throws NativSQLException {
        try {
            Object dbValue = rs.getObject(columnName);
            if (dbValue == null) {
                return null;
            }

            if (dbValue instanceof String str) {
                return Enum.valueOf(enumClass, str);
            }

            throw new NativSQLException("Cannot parse enum from value: " + dbValue.getClass().getSimpleName());

        } catch (IllegalArgumentException e) {
            throw new NativSQLException(
                    "Invalid enum value for " + enumClass.getSimpleName() + ": " + e.getMessage(), e);
        } catch (java.sql.SQLException e) {
            throw new NativSQLException(e);
        }
    }

    @Override
    public E fromValue(Object value, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params) {
        if (value == null)
            return null;
        if (value instanceof String str)
            return Enum.valueOf(enumClass, str);
        throw new NativSQLException(
                "Cannot parse " + enumClass.getSimpleName() + " from: " + value.getClass().getSimpleName());
    }

    @Override
    public Object toDatabase(E value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        if (value == null) {
            return null;
        }

        // Enum types must be converted to PGobject, no other conversion is allowed
        if (dataType != null) {
            throw new NativSQLException(
                    "Cannot convert enum " + enumClass.getSimpleName() + " to " + dataType);
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
