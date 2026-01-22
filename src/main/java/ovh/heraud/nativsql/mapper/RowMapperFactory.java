package ovh.heraud.nativsql.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.Join;
import ovh.heraud.nativsql.util.ReflectionUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Factory for creating and caching GenericRowMapper instances.
 * Performs class introspection once per type and caches the result.
 */
@Component
public class RowMapperFactory {

    private final Map<Class<?>, GenericRowMapper<?>> cache = new ConcurrentHashMap<>();

    public RowMapperFactory() {
    }

    /**
     * Gets or creates a GenericRowMapper for the specified class.
     *
     * @param clazz   the class to create a mapper for
     * @param dialect the database dialect for dialect-specific type mapping
     * @return a GenericRowMapper for the class
     */
    @SuppressWarnings({ "unchecked", "null" })
    @NonNull
    public <T> GenericRowMapper<T> getRowMapper(Class<T> clazz, DatabaseDialect dialect) {
        GenericRowMapper<?> cached = cache.get(clazz);
        if (cached != null) {
            return (GenericRowMapper<T>) cached;
        }
        GenericRowMapper<T> mapper = createRowMapper(clazz, dialect);
        cache.put(clazz, mapper);
        return mapper;
    }

    /**
     * Gets or creates a GenericRowMapper for the specified class with JOIN metadata.
     * Joins are auto-discovered from the class fields - no need to pass them explicitly.
     * The RowMapper will detect joined columns from the ResultSet metadata.
     *
     * @param clazz   the class to create a mapper for
     * @param dialect the database dialect for dialect-specific type mapping
     * @param joins   (deprecated, ignored) - joins are auto-discovered from class fields
     * @return a GenericRowMapper for the class
     */
    @NonNull
    public <T> GenericRowMapper<T> getRowMapper(Class<T> clazz, DatabaseDialect dialect, List<Join> joins) {
        // Joins parameter is ignored - RowMapper auto-discovers joined properties
        return getRowMapper(clazz, dialect);
    }

    /**
     * Creates a new GenericRowMapper by introspecting the class.
     * Automatically detects joined properties by examining all fields.
     */
    @NonNull
    private <T> GenericRowMapper<T> createRowMapper(Class<T> clazz, DatabaseDialect dialect) {
        List<PropertyMetadata<?>> simpleProperties = new ArrayList<>();
        Map<String, JoinedPropertyMetadata> subProperties = new HashMap<>();

        // Get all fields
        for (FieldAccessor fieldAccessor : ReflectionUtils.getFields(clazz).list()) {

            if (fieldAccessor.isSimpleType()) {
                ITypeMapper<?> typeMapper = dialect.getMapper(fieldAccessor.getType());

                if (typeMapper != null) {
                    // Simple type with a mapper
                    simpleProperties.add((PropertyMetadata<?>) new PropertyMetadata<>(
                            fieldAccessor, typeMapper, dialect));
                } else {
                    // Simple type without a mapper → likely a joined property
                    // Will be discovered by RowMapper at runtime by checking if the ResultSet
                    // contains columns with the property name prefix (e.g., "group.id")
                    GenericRowMapper<?> delegateMapper = getRowMapper(fieldAccessor.getType(), dialect);
                    subProperties.put(fieldAccessor.getName(),
                            new JoinedPropertyMetadata(fieldAccessor, delegateMapper));
                }
            }
            // OneToMany and List/Array fields are ignored by the mapper
        }

        return new GenericRowMapper<T>(clazz, simpleProperties, subProperties);
    }
}