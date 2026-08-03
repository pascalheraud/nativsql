package ovh.heraud.nativsql.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.ReflectionUtils;
import ovh.heraud.nativsql.util.TypeInfo;

/**
 * Factory for creating and caching RowMapper instances.
 * Performs class introspection once per type and caches the result.
 * Produces a {@link ScalarRowMapper} for base/JDBC scalar types (single-column
 * queries) and a {@link GenericRowMapper} for entity/bean types.
 */
@Component
public class RowMapperFactory {

    private final Map<Class<?>, RowMapper<?>> cache = new ConcurrentHashMap<>();

    @Autowired
    private AnnotationManager annotationManager;

    public RowMapperFactory() {
    }

    /**
     * Gets or creates a GenericRowMapper for the specified class.
     *
     * @param clazz               the class to create a mapper for
     * @param dialect             the database dialect for dialect-specific type
     *                            mapping
     * @param identifierConverter the identifier converter for column name
     *                            transformation
     * @return a GenericRowMapper for the class
     */
    public <T> RowMapper<T> getRowMapper(Class<T> clazz, DatabaseDialect dialect,
            IdentifierConverter identifierConverter) {
        @SuppressWarnings("unchecked")
        RowMapper<T> cached = (RowMapper<T>) cache.get(clazz);
        if (cached != null) {
            return cached;
        }
        ITypeMapper<T> scalarMapper = dialect.getMapperForType(clazz);
        RowMapper<T> mapper = scalarMapper != null
                ? new ScalarRowMapper<>(clazz, scalarMapper)
                : createRowMapper(clazz, dialect, identifierConverter);
        cache.put(clazz, mapper);
        return mapper;
    }

    /**
     * Gets or creates a GenericRowMapper for the specified bean/entity class,
     * bypassing the scalar-type check. Used for joined sub-properties, whose
     * declared field type is by construction never a base type (fields with a
     * dialect-mappable type never reach the joined-property branch).
     */
    @SuppressWarnings("unchecked")
    private <T> GenericRowMapper<T> getBeanRowMapper(Class<T> clazz, DatabaseDialect dialect,
            IdentifierConverter identifierConverter) {
        RowMapper<?> cached = cache.get(clazz);
        if (cached instanceof GenericRowMapper<?> genericCached) {
            return (GenericRowMapper<T>) genericCached;
        }
        GenericRowMapper<T> mapper = createRowMapper(clazz, dialect, identifierConverter);
        cache.put(clazz, mapper);
        return mapper;
    }

    /**
     * Creates a new GenericRowMapper by introspecting the class.
     * Automatically detects joined properties by examining all fields.
     */
    private <T> GenericRowMapper<T> createRowMapper(Class<T> clazz, DatabaseDialect dialect,
            IdentifierConverter identifierConverter) {
        List<PropertyMetadata<?>> simpleProperties = new ArrayList<>();
        Map<String, JoinedPropertyMetadata> subProperties = new HashMap<>();

        // Get all fields
        for (FieldAccessor<?> fieldAccessor : ReflectionUtils.getFields(clazz).list()) {

            // A field is simple if it's not annotated with @OneToMany
            boolean isSimple = annotationManager.getOneToManyInfo(fieldAccessor) == null;
            if (isSimple) {
                ITypeMapper<?> typeMapper = dialect.getMapper(fieldAccessor, annotationManager);

                if (typeMapper != null) {
                    // Simple type with a mapper
                    TypeInfo typeInfo = annotationManager.getTypeInfo(fieldAccessor);
                    simpleProperties.add((PropertyMetadata<?>) new PropertyMetadata<>(
                            fieldAccessor, typeMapper, identifierConverter, typeInfo));
                } else {
                    // Simple type without a mapper → likely a joined property
                    // Will be discovered by RowMapper at runtime by checking if the ResultSet
                    // contains columns with the property name prefix (e.g., "group.id")
                    GenericRowMapper<?> delegateMapper = getBeanRowMapper(fieldAccessor.getType(), dialect,
                            identifierConverter);
                    subProperties.put(fieldAccessor.getName(),
                            new JoinedPropertyMetadata(fieldAccessor, delegateMapper));
                }
            }
            // OneToMany and List/Array fields are ignored by the mapper
        }

        return new GenericRowMapper<T>(clazz, simpleProperties, subProperties);
    }
}