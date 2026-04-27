package ovh.heraud.nativsql.db.postgres.mapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.postgresql.util.PGobject;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * PostgreSQL-specific TypeMapper for composite types.
 * Handles reading from and writing to PostgreSQL composite type columns.
 *
 * @param <T> the composite type class
 */
public class PostgresCompositeTypeMapper<T> extends AbstractTypeMapper<T> {

    @SuppressWarnings("unchecked")
    @Override
    public T fromValue(Object value, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) throws ConversionException {
        Class<T> compositeClass = (Class<T>) fieldAccessor.getType();
        try {
            String compositeStr;
            if (value instanceof PGobject pgObject) {
                compositeStr = pgObject.getValue();
            } else if (value instanceof String str) {
                compositeStr = str;
            } else {
                throw new ConversionException(compositeClass);
            }
            return parseComposite(compositeStr, compositeClass);
        } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException e) {
            throw new ConversionException(compositeClass, e);
        }
    }

    private T parseComposite(String compositeStr, Class<T> compositeClass) throws ReflectiveOperationException {
        String trimmed = compositeStr.substring(1, compositeStr.length() - 1);
        String[] rawValues = trimmed.split(",", -1);
        Field[] fields = compositeClass.getDeclaredFields();

        Object[] convertedValues = new Object[Math.min(fields.length, rawValues.length)];
        for (int i = 0; i < convertedValues.length; i++) {
            String value = rawValues[i];
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            convertedValues[i] = convertValue(value, fields[i].getType());
        }

        try {
            T instance = compositeClass.getDeclaredConstructor().newInstance();
            for (int i = 0; i < convertedValues.length; i++) {
                fields[i].setAccessible(true);
                fields[i].set(instance, convertedValues[i]);
            }
            return instance;
        } catch (NoSuchMethodException e) {
            Class<?>[] types = java.util.Arrays.stream(fields)
                    .map(Field::getType).toArray(Class[]::new);
            java.lang.reflect.Constructor<T> ctor = compositeClass.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            return ctor.newInstance(convertedValues);
        }
    }

    @Override
    protected Object toDatabaseValue(T value, Map<ParamKey, Object> params)
            throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
        if (dataType != null) {
            throw new ConversionException(dataType.name());
        }
        try {
            List<String> fieldValues = new ArrayList<>();
            for (Field field : value.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object fieldValue = field.get(value);
                fieldValues.add(quoteCompositeValue(fieldValue));
            }
            String sqlTypeName = (String) params.get(TypeParamKey.SQL_TYPE);
            PGobject pgObject = new PGobject();
            pgObject.setType(sqlTypeName);
            pgObject.setValue("(" + String.join(",", fieldValues) + ")");
            return pgObject;
        } catch (java.sql.SQLException | IllegalAccessException e) {
            throw new ConversionException(value.getClass(), e);
        }
    }

    @Override
    public String formatParameter(String paramName, Map<ParamKey, Object> params) {
        String sqlTypeName = (String) params.get(TypeParamKey.SQL_TYPE);
        return "(:" + paramName + ")::" + sqlTypeName;
    }

    private String quoteCompositeValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            String str = (String) value;
            str = str.replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + str + "\"";
        }
        return value.toString();
    }

    private Object convertValue(String value, Class<?> targetType) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (String.class == targetType) {
            return value;
        } else if (Integer.class == targetType || int.class == targetType) {
            return Integer.parseInt(value);
        } else if (Long.class == targetType || long.class == targetType) {
            return Long.parseLong(value);
        } else if (Double.class == targetType || double.class == targetType) {
            return Double.parseDouble(value);
        } else if (Boolean.class == targetType || boolean.class == targetType) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }
}
