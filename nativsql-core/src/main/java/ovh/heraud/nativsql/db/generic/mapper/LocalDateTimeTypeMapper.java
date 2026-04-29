package ovh.heraud.nativsql.db.generic.mapper;

import java.time.LocalDateTime;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for LocalDateTime type with flexible conversion.
 * Converts from java.sql.Timestamp and java.util.Date.
 */
public class LocalDateTimeTypeMapper extends AbstractTypeMapper<LocalDateTime> {

    public LocalDateTimeTypeMapper() {
        super();
    }

    public LocalDateTimeTypeMapper(Map<TypeParamKey, Object> params) {
        super(params);
    }

    @Override
    public LocalDateTime fromValue(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof String str) return LocalDateTime.parse(str);
        throw new NativSQLException("Cannot convert " + value.getClass().getSimpleName() + " to LocalDateTime");
    }

    @Override
    protected LocalDateTime doMap(Object raw, Map<TypeParamKey, Object> params) throws NativSQLException {
        if (raw instanceof LocalDateTime ldt) return ldt;
        if (raw instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (raw instanceof java.util.Date date && !(raw instanceof java.sql.Date)) {
            return new java.sql.Timestamp(date.getTime()).toLocalDateTime();
        }
        if (raw instanceof java.sql.Date sqlDate && !(raw instanceof java.sql.Timestamp)) {
            return sqlDate.toLocalDate().atStartOfDay();
        }
        throw new NativSQLException("Cannot convert " + raw.getClass().getSimpleName() + " to LocalDateTime");
    }

    @Override
    protected Object toDatabaseValue(LocalDateTime value, DbDataType dataType, Map<TypeParamKey, Object> params) {
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
