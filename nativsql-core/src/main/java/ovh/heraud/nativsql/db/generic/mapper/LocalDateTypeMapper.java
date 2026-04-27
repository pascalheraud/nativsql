package ovh.heraud.nativsql.db.generic.mapper;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import ovh.heraud.nativsql.util.FieldAccessor;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;

import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for LocalDate type with flexible conversion.
 * Converts from java.sql.Date and java.util.Date (but NOT Timestamp, which
 * would lose time information).
 */
public class LocalDateTypeMapper extends AbstractTypeMapper<LocalDate> {

    @Override
    public LocalDate fromValue(Object value, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) throws ConversionException {
        if (value instanceof LocalDate ld)
            return ld;
        if (value instanceof String str) {
            try {
                return LocalDate.parse(str);
            } catch (DateTimeParseException e) {
                throw new ConversionException(LocalDate.class, e);
            }
        }
        if (value instanceof LocalDateTime ldt)
            return ldt.toLocalDate();
        if (value instanceof Date sqlDate)
            return sqlDate.toLocalDate();
        if (value instanceof java.util.Date utilDate)
            return new Date(utilDate.getTime()).toLocalDate();
        throw new ConversionException(LocalDate.class);
    }

    @Override
    protected Object toDatabaseValue(LocalDate value, Map<ParamKey, Object> params)
            throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> value.toString();
            case DATE -> value;
            case DATE_TIME -> value.atStartOfDay();
            case LOCAL_DATE_TIME -> value.atStartOfDay();
            default -> throw new ConversionException(dataType.name());
        };
    }
}
