package ovh.heraud.nativsql.db.generic.mapper;

import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * Mapper for enum types that handles reading String values from database
 * and writing to database using the appropriate dialect.
 *
 * @param <E> the enum type
 */
public class EnumStringMapper<E extends Enum<E>> extends AbstractTypeMapper<E> {

    private final Class<E> enumClass;

    public EnumStringMapper(Class<E> enumClass) {
        super();
        this.enumClass = enumClass;
    }

    public EnumStringMapper(Class<E> enumClass, Map<TypeParamKey, Object> params) {
        super(params);
        this.enumClass = enumClass;
    }

    @Override
    protected E doMap(Object raw, Map<TypeParamKey, Object> params) throws NativSQLException {
        if (raw instanceof String str) {
            try {
                return Enum.valueOf(enumClass, str);
            } catch (IllegalArgumentException e) {
                throw new NativSQLException(
                        "Invalid enum value for " + enumClass.getSimpleName() + ": " + str, e);
            }
        }
        throw new NativSQLException("Cannot parse enum from value type: " + raw.getClass().getSimpleName());
    }

    @Override
    protected Object toDatabaseValue(E value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        if (dataType == null) {
            return value.name();
        }

        return switch (dataType) {
            case STRING -> value.name();
            case IDENTITY -> throw new NativSQLException("IDENTITY type should not be passed to toDatabase");
            default -> throw new NativSQLException("Cannot convert Enum to " + dataType);
        };
    }
}
