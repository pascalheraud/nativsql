package ovh.heraud.nativsql.db.generic.mapper;

import java.sql.Date;
import java.sql.Timestamp;
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
 * TypeMapper for LocalDateTime type with flexible conversion.
 * Converts from java.sql.Timestamp and java.util.Date.
 */
public class LocalDateTimeTypeMapper extends AbstractTypeMapper<LocalDateTime> {

    @Override
    public LocalDateTime fromValue(Object raw, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) throws ConversionException {
        if (raw instanceof LocalDateTime ldt)
            return ldt;
        if (raw instanceof String str) {
            try {
                return LocalDateTime.parse(str);
            } catch (DateTimeParseException e) {
                throw new ConversionException(LocalDateTime.class, e);
            }
        }
        if (raw instanceof Timestamp ts)
            return ts.toLocalDateTime();
        if (raw instanceof java.util.Date date && !(raw instanceof Date)) {
            return new Timestamp(date.getTime()).toLocalDateTime();
        }
        if (raw instanceof Date sqlDate && !(raw instanceof Timestamp)) {
            return sqlDate.toLocalDate().atStartOfDay();
        }
        throw new ConversionException(LocalDateTime.class);
    }

    @Override
    protected Object toDatabaseValue(LocalDateTime value, Map<ParamKey, Object> params)
            throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
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
