package ovh.heraud.nativsql.db.generic.mapper;

import java.sql.ResultSet;
import java.time.LocalDate;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import org.springframework.jdbc.support.JdbcUtils;

/**
 * TypeMapper for LocalDate type with flexible conversion.
 * Converts from java.sql.Date and java.util.Date (but NOT Timestamp, which would lose time information).
 */
public class LocalDateTypeMapper implements ITypeMapper<LocalDate> {
    @Override
    public LocalDate map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            Object value = JdbcUtils.getResultSetValue(rs, index);
            if (value == null) return null;

            if (value instanceof LocalDate ld) {
                return ld;
            }
            // Accept java.sql.Date but NOT Timestamp
            if (value instanceof java.sql.Date sqlDate && !(value instanceof java.sql.Timestamp)) {
                return sqlDate.toLocalDate();
            }
            // Accept java.util.Date but NOT java.sql.Timestamp
            if (value instanceof java.util.Date utilDate && !(value instanceof java.sql.Timestamp)) {
                return new java.sql.Date(utilDate.getTime()).toLocalDate();
            }
            throw new NativSQLException("Unable to map column " + columnName + " with value " + value + " from class " + value.getClass() + " to LocalDate");
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to map column: " + columnName, e);
        }
    }

    @Override
    public Object toDatabase(LocalDate value, DbDataType dataType) {
        if (value == null) {
            return null;
        }

        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> value.toString();
            case DATE -> value;
            case DATE_TIME -> value.atStartOfDay();
            case LOCAL_DATE_TIME -> value.atStartOfDay();
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new NativSQLException("Cannot convert LocalDate to " + dataType);
        };
    }
}
