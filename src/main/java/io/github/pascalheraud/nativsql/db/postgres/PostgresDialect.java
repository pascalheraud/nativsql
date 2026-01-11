package io.github.pascalheraud.nativsql.db.postgres;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.postgresql.util.PGobject;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.pascalheraud.nativsql.db.DatabaseDialect;
import io.github.pascalheraud.nativsql.db.TypeRegistry;
import io.github.pascalheraud.nativsql.util.ReflectionUtils;

/**
 * PostgreSQL-specific implementation of DatabaseDialect.
 *
 * Handles PostgreSQL-specific SQL formatting and type conversions including:
 * - Enum types with :: casting syntax
 * - Composite types with (val1,val2,val3) format
 * - JSON/JSONB types
 */
public class PostgresDialect implements DatabaseDialect {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String formatEnumParameter(String paramName, String dbTypeName) {
        return "(:" + paramName + ")::" + dbTypeName;
    }

    @Override
    public String formatCompositeParameter(String paramName, String dbTypeName) {
        return "(:" + paramName + ")::" + dbTypeName;
    }

    @Override
    public Object convertEnumToSql(Enum<?> value, String dbTypeName) {
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType(dbTypeName);
            pgObject.setValue(value.name());
            return pgObject;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to convert enum to SQL", e);
        }
    }

    @Override
    public Object convertCompositeToSql(Object value, Class<?> valueClass, String dbTypeName,
                                         TypeRegistry registry) {
        try {
            List<String> fieldValues = new ArrayList<>();

            for (Field field : valueClass.getDeclaredFields()) {
                field.setAccessible(true);
                Object fieldValue = field.get(value);
                String quoted = quoteCompositeValue(fieldValue);
                fieldValues.add(quoted);
            }

            PGobject pgObject = new PGobject();
            pgObject.setType(dbTypeName);
            pgObject.setValue("(" + String.join(",", fieldValues) + ")");
            return pgObject;
        } catch (IllegalAccessException | SQLException e) {
            throw new RuntimeException("Failed to convert composite to SQL", e);
        }
    }

    @Override
    public Object convertJsonToSql(Object value) {
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType("jsonb");
            pgObject.setValue(objectMapper.writeValueAsString(value));
            return pgObject;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert value to JSON", e);
        }
    }

    @Override
    public <T> T parseCompositeType(Object dbValue, Class<T> targetType,
                                     TypeRegistry registry) {
        try {
            if (!(dbValue instanceof PGobject)) {
                throw new RuntimeException("Expected PGobject for composite type, got: " + dbValue.getClass());
            }

            PGobject pgObject = (PGobject) dbValue;
            String compositeStr = pgObject.getValue();

            // Remove outer parentheses and split by comma
            String trimmed = compositeStr.substring(1, compositeStr.length() - 1);
            String[] values = trimmed.split(",", -1);

            T instance = targetType.getDeclaredConstructor().newInstance();
            Field[] fields = targetType.getDeclaredFields();

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
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse composite type", e);
        }
    }

    @Override
    public <E extends Enum<E>> E parseEnum(Object dbValue, Class<E> enumClass) {
        try {
            if (dbValue instanceof PGobject) {
                String enumValue = ((PGobject) dbValue).getValue();
                return Enum.valueOf(enumClass, enumValue);
            } else if (dbValue instanceof String) {
                return Enum.valueOf(enumClass, (String) dbValue);
            }
            throw new RuntimeException("Cannot parse enum from value: " + dbValue);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid enum value", e);
        }
    }

    @Override
    public Object parseJson(Object dbValue, Class<?> targetType) {
        try {
            String jsonStr;
            if (dbValue instanceof PGobject) {
                jsonStr = ((PGobject) dbValue).getValue();
            } else if (dbValue instanceof String) {
                jsonStr = (String) dbValue;
            } else {
                return dbValue;
            }

            return objectMapper.readValue(jsonStr, targetType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON value", e);
        }
    }

    @Override
    public String getRegisteredEnumType(Class<?> enumType) {
        // This would normally query the registry, but we don't have direct access to it here
        // The actual lookup happens in PostgresTypeRegistry
        return null;
    }

    @Override
    public String getRegisteredCompositeType(Class<?> compositeType) {
        // This would normally query the registry, but we don't have direct access to it here
        // The actual lookup happens in PostgresTypeRegistry
        return null;
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
