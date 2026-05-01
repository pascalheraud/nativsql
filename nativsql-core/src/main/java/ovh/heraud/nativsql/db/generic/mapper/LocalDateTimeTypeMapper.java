package ovh.heraud.nativsql.db.generic.mapper;

import java.time.LocalDateTime;
import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * TypeMapper for LocalDateTime type with flexible conversion.
 * Converts from java.sql.Timestamp and java.util.Date.
 */
public class LocalDateTimeTypeMapper extends AbstractTypeMapper<LocalDateTime> {

    @Override
    public LocalDateTime fromValue(Object raw, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params) throws ConversionException {
        if (raw == null)
            return null;
        if (raw instanceof LocalDateTime ldt)
            return ldt;
        if (raw instanceof String str && dataType == DbDataType.ENCRYPTED) {
            try {
                return LocalDateTime.parse(str);
            } catch (java.time.format.DateTimeParseException e) {
                throw new ConversionException(LocalDateTime.class, e);
            }
        }
        if (raw instanceof java.sql.Timestamp ts)
            return ts.toLocalDateTime();
        if (raw instanceof java.util.Date date && !(raw instanceof java.sql.Date)) {
            return new java.sql.Timestamp(date.getTime()).toLocalDateTime();
        }
        if (raw instanceof java.sql.Date sqlDate && !(raw instanceof java.sql.Timestamp)) {
            return sqlDate.toLocalDate().atStartOfDay();
        }
        throw new ConversionException(LocalDateTime.class);
    }

    @Override
    protected Object toDatabaseValue(LocalDateTime value, DbDataType dataType, Map<TypeParamKey, Object> params)
            throws ConversionException {
        if (dataType == null) {
            return value;
        }

        return switch (dataType) {
            case STRING -> value.toString();
            case DATE -> value.toLocalDate();
            case DATE_TIME -> value;
            case LOCAL_DATE_TIME -> value;
            default -> throw new ConversionException(dataType.name());
        };
    }
}
