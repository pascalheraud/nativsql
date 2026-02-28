package ovh.heraud.nativsql.db.generic.mapper;

import java.sql.ResultSet;
import java.time.LocalDateTime;

import org.springframework.jdbc.support.JdbcUtils;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * TypeMapper for LocalDateTime type with flexible conversion.
 * Converts from java.sql.Timestamp and java.util.Date (but NOT java.sql.Date alone, which would add a default time).
 */
public class LocalDateTimeTypeMapper implements ITypeMapper<LocalDateTime> {
    @Override
    public LocalDateTime map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            Object value = JdbcUtils.getResultSetValue(rs, index);
            if (value == null) return null;

            if (value instanceof LocalDateTime ldt) {
                return ldt;
            }
            // Accept java.sql.Timestamp
            if (value instanceof java.sql.Timestamp ts) {
                return ts.toLocalDateTime();
            }
            // Accept java.util.Date but NOT pure java.sql.Date (which would lose time)
            if (value instanceof java.util.Date date && !(value instanceof java.sql.Date)) {
                return new java.sql.Timestamp(date.getTime()).toLocalDateTime();
            }
            // Also accept java.sql.Date by converting with default midnight time
            if (value instanceof java.sql.Date sqlDate && !(value instanceof java.sql.Timestamp)) {
                return sqlDate.toLocalDate().atStartOfDay();
            }
            throw new NativSQLException("Unable to map column " + columnName + " with value " + value + " from class " + value.getClass() + " to LocalDateTime");
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to map column: " + columnName, e);
        }
    }

    @Override
    public Object toDatabase(LocalDateTime value, DbDataType dataType) {
        if (value == null) {
            return null;
        }

        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> value.toString();
            case DATE -> value.toLocalDate();
            case DATE_TIME -> value;
            case LOCAL_DATE_TIME -> value;
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new NativSQLException("Cannot convert LocalDateTime to " + dataType);
        };
    }
}
