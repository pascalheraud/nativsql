package ovh.heraud.nativsql.db.postgres.mapper;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import org.postgresql.util.PGobject;

/**
 * PostgreSQL-specific TypeMapper for composite types.
 * Handles reading from and writing to PostgreSQL composite type columns.
 *
 * @param <T> the composite type class
 */
public class PostgresCompositeTypeMapper<T> implements ITypeMapper<T> {

    private final Class<T> compositeClass;
    private final String dbTypeName;

    public PostgresCompositeTypeMapper(Class<T> compositeClass, String dbTypeName) {
        this.compositeClass = compositeClass;
        this.dbTypeName = dbTypeName;
    }

    @Override
    public T map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            Object dbValue = rs.getObject(columnName);
            if (dbValue == null) {
                return null;
            }

            // PostgreSQL returns PGobject for composite types
            if (!(dbValue instanceof PGobject)) {
                throw new java.sql.SQLException("Expected PGobject for composite type, got: " + dbValue.getClass());
            }

            PGobject pgObject = (PGobject) dbValue;
            String compositeStr = pgObject.getValue();

            // Remove outer parentheses and split by comma
            String trimmed = compositeStr.substring(1, compositeStr.length() - 1);
            String[] values = trimmed.split(",", -1);

            T instance = compositeClass.getDeclaredConstructor().newInstance();
            Field[] fields = compositeClass.getDeclaredFields();

            for (int i = 0; i < fields.length && i < values.length; i++) {
                Field field = fields[i];
                field.setAccessible(true);

                String value = values[i];
                // Remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }

                Object convertedValue = convertValue(value, field.getType());
                field.set(instance, convertedValue);
            }

            return instance;
        } catch (java.sql.SQLException e) {
            throw new NativSQLException(e);
        } catch (ReflectiveOperationException e) {
            throw new NativSQLException(e);
        } catch (IllegalArgumentException e) {
            throw new NativSQLException(e);
        } catch (SecurityException e) {
            throw new NativSQLException(e);
        }
    }

    @Override
    public Object toDatabase(T value) {
        if (value == null) {
            return null;
        }
        try {
            List<String> fieldValues = new ArrayList<>();

            for (Field field : compositeClass.getDeclaredFields()) {
                field.setAccessible(true);
                Object fieldValue = field.get(value);
                String quoted = quoteCompositeValue(fieldValue);
                fieldValues.add(quoted);
            }

            PGobject pgObject = new PGobject();
            pgObject.setType(dbTypeName);
            pgObject.setValue("(" + String.join(",", fieldValues) + ")");
            return pgObject;
        } catch (java.sql.SQLException | IllegalAccessException e) {
            throw new RuntimeException("Failed to convert composite to SQL", e);
        }
    }

    @Override
    public String formatParameter(String paramName) {
        // PostgreSQL composite types use :: casting syntax
        return "(:" + paramName + ")::" + dbTypeName;
    }

    /**
     * Quotes a value for use in a PostgreSQL composite type string.
     * Handles null values, strings, and other types.
     */
    private String quoteCompositeValue(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof String) {
            String str = (String) value;
            // Escape backslashes and quotes
            str = str.replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + str + "\"";
        }

        return value.toString();
    }

    /**
     * Converts a string value to the target type.
     */
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
