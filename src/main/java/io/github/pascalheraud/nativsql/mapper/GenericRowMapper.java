package io.github.pascalheraud.nativsql.mapper;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.pascalheraud.nativsql.exception.NativSQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Generic RowMapper that uses reflection and introspection to map ResultSet
 * rows to Java objects.
 * Supports JOINed tables with dot notation in column names (e.g., "group.id").
 *
 * @param <T> the entity type to map
 */
public class GenericRowMapper<T> implements RowMapper<T> {

    private final Class<T> rootClass;
    private final Map<String, PropertyMetadata<?>> simpleProperties;
    private final Map<String, JoinedPropertyMetadata> subProperties;

    public GenericRowMapper(Class<T> rootClass,
            List<PropertyMetadata<?>> simpleProperties) {
        this(rootClass, simpleProperties, Map.of());
    }

    public GenericRowMapper(Class<T> rootClass,
            List<PropertyMetadata<?>> simpleProperties,
            Map<String, JoinedPropertyMetadata> subProperties) {
        this.rootClass = rootClass;

        // Build map of simple properties indexed by Java property name (camelCase)
        // Since all SQL queries return Java identifiers in AS aliases
        Map<String, PropertyMetadata<?>> simplePropsByColumn = new HashMap<>();
        for (PropertyMetadata<?> prop : simpleProperties) {
            simplePropsByColumn.put(prop.getFieldAccessor().getName(), prop);
        }
        this.simpleProperties = simplePropsByColumn;

        // Build map of sub-properties indexed by name
        Map<String, JoinedPropertyMetadata> subPropsByName = new HashMap<>();
        for (JoinedPropertyMetadata joined : subProperties.values()) {
            subPropsByName.put(joined.getPropertyName(), joined);
        }
        this.subProperties = subPropsByName;
    }

    @Override
    @Nullable
    public T mapRow(@NonNull ResultSet rs, int rowNum) throws NativSQLException {
        try {
            T instance = null;

            // Single loop through ResultSet columns
            java.sql.ResultSetMetaData metadata = rs.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                String columnLabel = metadata.getColumnLabel(i);

                if (columnLabel.contains(".")) {
                    // This is a joined property column (e.g., "group.id")
                    String prefix = columnLabel.substring(0, columnLabel.indexOf("."));
                    String columnName = columnLabel.substring(columnLabel.indexOf(".") + 1);
                    JoinedPropertyMetadata joined = subProperties.get(prefix);

                    if (joined != null) {
                        // Lazy-initialize root instance if needed
                        if (instance == null) {
                            instance = newInstance(rootClass);
                        }

                        // Get or create sub-object instance via delegated mapper
                        Object subInstance = joined.getFieldAccessor().getValue(instance);
                        if (subInstance == null) {
                            subInstance = createSubInstance(joined);
                            joined.getFieldAccessor().setValue(instance, subInstance);
                        }

                        // Map this single column to the sub-object using the delegated mapper
                        joined.getDelegateMapper().mapColumn(subInstance, columnName, rs, columnLabel, prefix);
                    }
                } else {
                    // This is a simple property column
                    if (instance == null) {
                        instance = newInstance(rootClass);
                    }
                    mapColumn(instance, columnLabel, rs, columnLabel, null);
                }
            }

            return instance;

        } catch (ReflectiveOperationException | java.sql.SQLException e) {
            throw new NativSQLException("Failed to map row to " + rootClass.getSimpleName(), e);
        }
    }

    /**
     * Creates a new instance of the given class using its no-arg constructor.
     */
    private <U> U newInstance(Class<U> clazz) throws ReflectiveOperationException {
        return clazz.getDeclaredConstructor().newInstance();
    }

    /**
     * Creates a new sub-instance using the delegated mapper.
     */
    private Object createSubInstance(JoinedPropertyMetadata joined) throws ReflectiveOperationException {
        return joined.getDelegateMapper().createNewInstance(joined.getPropertyType());
    }

    /**
     * Creates a new instance of the given class.
     * Public method to allow sub-mappers to create instances.
     */
    public <U> U createNewInstance(Class<U> clazz) throws ReflectiveOperationException {
        return newInstance(clazz);
    }

    /**
     * Maps a single column value to a simple property of a target object.
     *
     * @param targetObject the object to set the property on
     * @param propertyColumnName the column name to search for in this mapper
     * @param rs the result set
     * @param columnLabel the actual column label from the result set
     * @param prefix the prefix for this property (e.g., "group" for "group.id"), or null for root properties
     * @throws NativSQLException if the property metadata is not found
     */
    private void mapColumn(Object targetObject, String propertyColumnName, ResultSet rs, String columnLabel, String prefix)
            throws NativSQLException {
        PropertyMetadata<?> prop = simpleProperties.get(propertyColumnName);

        if (prop == null) {
            throw new NativSQLException("Property metadata not found for column: " + propertyColumnName);
        }

        Object value = prop.getTypeMapper().map(rs, columnLabel);
        prop.getFieldAccessor().setValue(targetObject, value);
    }

}