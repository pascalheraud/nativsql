package ovh.heraud.nativsql.db.postgres;

import java.lang.annotation.Annotation;
import java.util.UUID;

import ovh.heraud.nativsql.annotation.EnumMapping;
import ovh.heraud.nativsql.db.AbstractChainedDialect;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.generic.GenericDialect;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresEnumMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgreJSONTypeMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresCompositeTypeMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresUUIDTypeMapper;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * PostgreSQL-specific implementation of DatabaseDialect.
 *
 * Handles PostgreSQL-specific SQL formatting and type conversions including:
 * - Enum types with :: casting syntax
 * - Composite types with (val1,val2,val3) format
 * - JSON/JSONB types
 * - UUID types
 *
 * Part of the Chain of Responsibility pattern, chains to DefaultDialect for unmapped types.
 * Can be extended to create specialized PostgreSQL dialects (e.g., with PostGIS support).
 */
public class PostgresDialect extends AbstractChainedDialect {

    /**
     * Create a PostgreSQL dialect that chains to the default dialect.
     *
     * @param nextDialect the next dialect to delegate to
     */
    public PostgresDialect(DatabaseDialect nextDialect) {
        super(nextDialect);
    }

    /**
     * Create a PostgreSQL dialect with a generic dialect as the next in chain.
     */
    public PostgresDialect() {
        this(new GenericDialect());
    }

    @Override
    public <E extends Enum<E>> ITypeMapper<E> getEnumMapper(Class<E> enumClass) {
        // Check if enum type is registered programmatically
        String dbTypeName = enumDbTypes.get(enumClass);

        // If not in map, check for annotation
        if (dbTypeName == null) {
            // Look for EnumPGMapping annotation
            Annotation[] annotations = enumClass.getAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation instanceof EnumMapping enumMapping) {
                    dbTypeName = enumMapping.pgTypeName();
                    // Cache it in the map for next time
                    enumDbTypes.put(enumClass, dbTypeName);
                }
            }
        }

        return new PostgresEnumMapper<E>(enumClass, dbTypeName);
    }

    @Override
    public <T> ITypeMapper<T> getJsonMapper(Class<T> jsonClass) {
        // PostgreSQL uses the dedicated PostgreJSONTypeMapper
        return new PostgreJSONTypeMapper<>(jsonClass);
    }

    @Override
    public <T> ITypeMapper<T> getCompositeMapper(Class<T> compositeClass) {
        // Check if composite type is registered programmatically
        String dbTypeName = compositeTypes.get(compositeClass);

        if (dbTypeName == null) {
            throw new RuntimeException("Composite type not registered: " + compositeClass.getName());
        }

        // PostgreSQL uses the dedicated PostgresCompositeTypeMapper
        return new PostgresCompositeTypeMapper<>(compositeClass, dbTypeName);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> ITypeMapper<T> getMapper(Class<T> targetType) {
        // Check for composite types first (PostgreSQL-specific)
        if (compositeTypes.containsKey(targetType)) {
            return getCompositeMapper(targetType);
        }

        // Check for registered JSON types
        if (jsonTypes.containsKey(targetType)) {
            return getJsonMapper(targetType);
        }

        // Use PostgreSQL-specific UUID mapper
        if (targetType == UUID.class) {
            return (ITypeMapper<T>) new PostgresUUIDTypeMapper();
        }

        // Check for enum types (registered or annotated)
        if (targetType.isEnum()) {
            Class<? extends Enum> enumClass = (Class<? extends Enum>) targetType;
            return (ITypeMapper<T>) getEnumMapperForType(enumClass);
        }

        // Fall back to next dialect in chain for other types
        return super.getMapper(targetType);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <E extends Enum<E>> ITypeMapper<E> getEnumMapperForType(Class<? extends Enum> enumClass) {
        return getEnumMapper((Class<E>) enumClass);
    }
}
