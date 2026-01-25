package ovh.heraud.nativsql.db.postgres;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.EnumMapping;
import ovh.heraud.nativsql.db.DefaultDialect;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL-specific implementation of DatabaseDialect.
 *
 * Handles PostgreSQL-specific SQL formatting and type conversions including:
 * - Enum types with :: casting syntax
 * - Composite types with (val1,val2,val3) format
 * - JSON/JSONB types
 */
@Component
public class PostgresDialect extends DefaultDialect {

    private final Map<Class<?>, String> enumTypeNames = new HashMap<>();
    private final Map<Class<?>, String> compositeTypeNames = new HashMap<>();

    public <E extends Enum<E>> void registerEnumType(Class<E> enumClass, String pgTypeName) {
        enumTypeNames.put(enumClass, pgTypeName);
    }

    public <T> void registerCompositeType(Class<T> compositeClass, String pgTypeName) {
        compositeTypeNames.put(compositeClass, pgTypeName);
    }

    @Override
    public <E extends Enum<E>> ITypeMapper<E> getEnumMapper(Class<E> enumClass) {
        // Check if enum type is registered programmatically
        String dbTypeName = enumTypeNames.get(enumClass);

        // If not in map, check for annotation
        if (dbTypeName == null) {
            // Look for EnumPGMapping annotation
            Annotation[] annotations = enumClass.getAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation instanceof EnumMapping enumMapping) {
                    dbTypeName = enumMapping.pgTypeName();
                    // Cache it in the map for next time
                    enumTypeNames.put(enumClass, dbTypeName);
                }
            }
        }

        return new PGEnumMapper<E>(enumClass, dbTypeName);
    }

    @Override
    public <T> ITypeMapper<T> getJsonMapper(Class<T> jsonClass) {
        // PostgreSQL uses the dedicated PostgreJSONTypeMapper
        return new PostgreJSONTypeMapper<>(jsonClass);
    }

    @Override
    public <T> ITypeMapper<T> getCompositeMapper(Class<T> compositeClass) {
        // Check if composite type is registered programmatically
        String dbTypeName = compositeTypeNames.get(compositeClass);

        if (dbTypeName == null) {
            throw new RuntimeException("Composite type not registered: " + compositeClass.getName());
        }

        // PostgreSQL uses the dedicated PostgresCompositeTypeMapper
        return new PostgresCompositeTypeMapper<>(compositeClass, dbTypeName);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ITypeMapper<T> getMapper(Class<T> targetType) {
        // Check for composite types first (PostgreSQL-specific)
        if (compositeTypeNames.containsKey(targetType)) {
            return getCompositeMapper(targetType);
        }

        // Use PostgreSQL-specific UUID mapper
        if (targetType == UUID.class) {
            return (ITypeMapper<T>) new PostgresUUIDTypeMapper();
        }

        // Fall back to parent implementation for enums and JSON types
        return super.getMapper(targetType);
    }
}
