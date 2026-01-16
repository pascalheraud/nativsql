package io.github.pascalheraud.nativsql.mapper;

import io.github.pascalheraud.nativsql.exception.NativSQLException;

/**
 * Mapper for enum types that handles reading String values from database
 * and writing to database using the appropriate dialect.
 *
 * @param <E> the enum type
 */
public class EnumStringMapper<E extends Enum<E>> implements ITypeMapper<E> {

    private final Class<E> enumClass;

    public EnumStringMapper(Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public E map(java.sql.ResultSet rs, String columnName) throws NativSQLException {
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
        return value.name();
    }

    @Override
    public String formatParameter(String paramName) {
        // MySQL and default behavior: just return the parameter name without casting
        return ":" + paramName;
    }
}
