package ovh.heraud.nativsql.db.generic.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for LocalDate type with flexible conversion.
 * Converts from java.sql.Date and java.util.Date (but NOT Timestamp, which would lose time information).
 */
public class LocalDateTypeMapper extends AbstractTypeMapper<LocalDate> {

    public LocalDateTypeMapper() {
        super();
    }

    public LocalDateTypeMapper(Map<TypeParamKey, Object> params) {
        super(params);
    }

    @Override
    public LocalDate fromValue(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof String str) return LocalDate.parse(str);
        throw new NativSQLException("Cannot convert " + value.getClass().getSimpleName() + " to LocalDate");
    }

    @Override
    protected LocalDate doMap(Object raw, Map<TypeParamKey, Object> params) throws NativSQLException {
        if (raw instanceof LocalDate ld) return ld;
        if (raw instanceof LocalDateTime ldt) return ldt.toLocalDate();
        if (raw instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (raw instanceof java.util.Date utilDate) return new java.sql.Date(utilDate.getTime()).toLocalDate();
        throw new NativSQLException("Cannot convert " + raw.getClass().getSimpleName() + " to LocalDate");
    }

    @Override
    protected Object toDatabaseValue(LocalDate value, DbDataType dataType, Map<TypeParamKey, Object> params) {
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
