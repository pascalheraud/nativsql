package ovh.heraud.nativsql.db.postgres;

import java.util.UUID;

import org.jspecify.annotations.NonNull;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.AbstractChainedDialect;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.generic.GenericDialect;
import ovh.heraud.nativsql.db.postgres.mapper.PostgreJSONTypeMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresByteArrayTypeMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresCompositeTypeMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresEnumMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresStringTypeMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresUUIDTypeMapper;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.util.EnumMappingInfo;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.TypeInfo;

/**
 * PostgreSQL-specific implementation of DatabaseDialect.
 *
 * Handles PostgreSQL-specific SQL formatting and type conversions including:
 * - Enum types with :: casting syntax
 * - Composite types with (val1,val2,val3) format
 * - JSON/JSONB types
 * - UUID types
 *
 * Part of the Chain of Responsibility pattern, chains to DefaultDialect for
 * unmapped types.
 * Can be extended to create specialized PostgreSQL dialects (e.g., with PostGIS
 * support).
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
    public <E extends Enum<E>> ITypeMapper<E> getEnumMapper(Class<E> enumClass, AnnotationManager annotationManager) {
        String dbTypeName = null;

        // Check for annotation
        if (annotationManager != null) {
            EnumMappingInfo enumMappingInfo = annotationManager.getEnumMappingInfo(enumClass);
            if (enumMappingInfo != null) {
                dbTypeName = enumMappingInfo.getTypeName();
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
    public <T> ITypeMapper<T> getCompositeMapper(Class<T> compositeClass, AnnotationManager annotationManager) {

        ovh.heraud.nativsql.util.CompositeTypeInfo compositeTypeInfo = annotationManager
                .getCompositeTypeInfo(compositeClass);
        if (compositeTypeInfo == null) {
            throw new RuntimeException("Composite type not registered: " + compositeClass.getName());
        }
        String dbTypeName = compositeTypeInfo.getTypeName();

        // PostgreSQL uses the dedicated PostgresCompositeTypeMapper
        return new PostgresCompositeTypeMapper<>(compositeClass, dbTypeName);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> ITypeMapper<T> getMapper(FieldAccessor<T> fieldAccessor,@NonNull AnnotationManager annotationManager) {
        Class<T> targetType = (Class<T>) fieldAccessor.getType();
        // Check for composite types first (PostgreSQL-specific)
        if (annotationManager.getCompositeTypeInfo(targetType) != null) {
            return getCompositeMapper(targetType, annotationManager);
        }

        // Check for registered JSON types
        if (annotationManager.getJsonInfo(targetType) != null) {
            return getJsonMapper(targetType);
        }

        // Use PostgreSQL-specific UUID mapper
        if (targetType == UUID.class) {
           TypeInfo typeInfo = annotationManager.getTypeInfo(fieldAccessor);
            return (ITypeMapper<T>) new PostgresUUIDTypeMapper(typeInfo);
        }
        // For strings, use the Postgres-specific string mapper which knows how to
        // wrap UUID literals in PGobject when writing to a UUID column.
        if (targetType == String.class) {
            return (ITypeMapper<T>) new PostgresStringTypeMapper();
        }
        // For byte[] we also provide a PostgreSQL specialisation supporting UUID
        if (targetType == byte[].class) {
            return (ITypeMapper<T>) new PostgresByteArrayTypeMapper();
        }

        // Check for enum types (registered or annotated)
        if (targetType.isEnum()) {
            Class<? extends Enum> enumClass = (Class<? extends Enum>) targetType;
            return (ITypeMapper<T>) getEnumMapperForType(enumClass, annotationManager);
        }

        // Fall back to next dialect in chain for other types
        return super.getMapper(fieldAccessor, annotationManager);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <E extends Enum<E>> ITypeMapper<E> getEnumMapperForType(Class<? extends Enum> enumClass, AnnotationManager annotationManager) {
        return getEnumMapper((Class<E>) enumClass, annotationManager);
    }
}
