package ovh.heraud.nativsql.db.generic.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
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
    public LocalDate fromValue(Object value, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params) throws ConversionException {
        if (value == null)
            return null;
        if (value instanceof LocalDate ld)
            return ld;
        if (value instanceof String str && dataType == DbDataType.ENCRYPTED) {
            try {
                return LocalDate.parse(str);
            } catch (java.time.format.DateTimeParseException e) {
                throw new ConversionException(LocalDate.class, e);
            }
        }
        if (value instanceof LocalDateTime ldt)
            return ldt.toLocalDate();
        if (value instanceof java.sql.Date sqlDate)
            return sqlDate.toLocalDate();
        if (value instanceof java.util.Date utilDate)
            return new java.sql.Date(utilDate.getTime()).toLocalDate();
        throw new ConversionException(LocalDate.class);
    }

    @Override
    protected Object toDatabaseValue(LocalDate value, DbDataType dataType, Map<TypeParamKey, Object> params)
            throws ConversionException {
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
