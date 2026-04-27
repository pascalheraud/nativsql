package ovh.heraud.nativsql.db.generic.mapper;

import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * Mapper for enum types that handles reading String values from database
 * and writing to database using the appropriate dialect.
 *
 * @param <E> the enum type
 */
public class EnumStringMapper<E extends Enum<E>> extends AbstractTypeMapper<E> {

    @SuppressWarnings("unchecked")
    @Override
    public E fromValue(Object raw, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) throws ConversionException {
        Class<E> enumClass = (Class<E>) fieldAccessor.getType();
        if (raw instanceof String str) {
            try {
                return Enum.valueOf(enumClass, str);
            } catch (IllegalArgumentException e) {
                throw new ConversionException(enumClass, e);
            }
        }
        throw new ConversionException(enumClass);
    }

    @Override
    protected Object toDatabaseValue(E value, Map<ParamKey, Object> params)
            throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
        if (dataType == null) {
            return value.name();
        }

        return switch (dataType) {
            case STRING -> value.name();
            case IDENTITY -> throw new ConversionException(dataType.name());
            default -> throw new ConversionException(dataType.name());
        };
    }
}
