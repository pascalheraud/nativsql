package ovh.heraud.nativsql.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.ReflectionUtils;

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
     * @param clazz               the class to create a mapper for
     * @param dialect             the database dialect for dialect-specific type mapping
     * @param identifierConverter the identifier converter for column name transformation
     * @return a GenericRowMapper for the class
     */
    @SuppressWarnings({ "unchecked"})
    public <T> GenericRowMapper<T> getRowMapper(Class<T> clazz, DatabaseDialect dialect, IdentifierConverter identifierConverter) {
        GenericRowMapper<?> cached = cache.get(clazz);
        if (cached != null) {
            return (GenericRowMapper<T>) cached;
        }
        GenericRowMapper<T> mapper = createRowMapper(clazz, dialect, identifierConverter);
        mapper.toString();
        cache.put(clazz, mapper);
        return mapper;
    }

    /**
     * Creates a new GenericRowMapper by introspecting the class.
     * Automatically detects joined properties by examining all fields.
     */
    private <T> GenericRowMapper<T> createRowMapper(Class<T> clazz, DatabaseDialect dialect, IdentifierConverter identifierConverter) {
        List<PropertyMetadata<?>> simpleProperties = new ArrayList<>();
        Map<String, JoinedPropertyMetadata> subProperties = new HashMap<>();

        // Get all fields
        for (FieldAccessor fieldAccessor : ReflectionUtils.getFields(clazz).list()) {

            if (fieldAccessor.isSimpleType()) {
                ITypeMapper<?> typeMapper = dialect.getMapper(fieldAccessor.getType());

                if (typeMapper != null) {
                    // Simple type with a mapper
                    simpleProperties.add((PropertyMetadata<?>) new PropertyMetadata<>(
                            fieldAccessor, typeMapper, identifierConverter));
                } else {
                    // Simple type without a mapper → likely a joined property
                    // Will be discovered by RowMapper at runtime by checking if the ResultSet
                    // contains columns with the property name prefix (e.g., "group.id")
                    GenericRowMapper<?> delegateMapper = getRowMapper(fieldAccessor.getType(), dialect, identifierConverter);
                    subProperties.put(fieldAccessor.getName(),
                            new JoinedPropertyMetadata(fieldAccessor, delegateMapper));
                }
            }
            // OneToMany and List/Array fields are ignored by the mapper
        }

        return new GenericRowMapper<T>(clazz, simpleProperties, subProperties);
    }
}