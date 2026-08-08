package ovh.heraud.nativsql.db.postgres.mapper;

import java.util.Map;

import org.postgresql.util.PGobject;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * PostgreSQL-specific mapper for enum types that handles both reading from
 * database
 * and writing to database with proper type casting.
 *
 * @param <E> the enum type
 */
public class PostgresEnumMapper<E extends Enum<E>> extends AbstractTypeMapper<E> {

    @SuppressWarnings("unchecked")
    @Override
    public E fromValue(Object value, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) throws ConversionException {
        Class<E> enumClass = (Class<E>) fieldAccessor.getType();
        if (value instanceof String str) {
            E enumRes = Enum.valueOf(enumClass, str);
            if (enumRes == null) {
                throw new ConversionException(enumClass,
                        new IllegalArgumentException("No enum constant " + enumClass.getName() + "." + str));
            }
            return enumRes;
        }
        throw new ConversionException(enumClass);
    }

    @Override
    protected Object toDatabaseValue(E value, Map<ParamKey, Object> params)
            throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
        if (dataType != null) {
            throw new ConversionException(dataType.name());
        }
        String sqlTypeName = (String) params.get(TypeParamKey.SQL_TYPE);
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType(sqlTypeName);
            pgObject.setValue(value.name());
            return pgObject;
        } catch (java.sql.SQLException e) {
            throw new ConversionException(PGobject.class, e);
        }
    }

    @Override
    public String formatParameter(String paramName, Map<ParamKey, Object> params) {
        String sqlTypeName = (String) params.get(TypeParamKey.SQL_TYPE);
        return PostgresParameterCasts.cast(paramName, sqlTypeName);
    }
}
