package io.github.pascalheraud.nativsql.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;

/**
 * Generic RowMapper that uses reflection and introspection to map ResultSet rows to Java objects.
 * Supports nested objects using dot notation in column names (e.g., "address.street").
 * 
 * @param <T> the entity type to map
 */
public class GenericRowMapper<T> implements RowMapper<T> {
    
    private final Class<T> rootClass;
    private final List<PropertyMetadata> simpleProperties;
    private final Map<String, NestedPropertyMetadata> nestedProperties;
    
    public GenericRowMapper(Class<T> rootClass,
                           List<PropertyMetadata> simpleProperties,
                           Map<String, NestedPropertyMetadata> nestedProperties) {
        this.rootClass = rootClass;
        this.simpleProperties = simpleProperties;
        this.nestedProperties = nestedProperties;
    }
    
    @Override
    public T mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
        try {
            T instance = null;
            
            // Map simple properties
            for (PropertyMetadata prop : simpleProperties) {
                // Check if column exists first before attempting to map
                if (!columnExists(rs, prop.getColumnName())) {
                    continue;
                }

                try {
                    Object value = prop.getTypeMapper().map(rs, prop.getColumnName());

                    // Lazy instantiation: create instance only if we have at least one value
                    if (value != null) {
                        if (instance == null) {
                            instance = rootClass.getDeclaredConstructor().newInstance();
                        }
                        prop.getSetter().invoke(instance, value);
                    } else {
                        // Column exists but value is NULL
                        if (instance == null) {
                            instance = rootClass.getDeclaredConstructor().newInstance();
                        }
                    }
                } catch (SQLException e) {
                    // Column mapping failed, skip
                }
            }
            
            // Map nested properties
            for (NestedPropertyMetadata nested : nestedProperties.values()) {
                try {
                    ResultSet prefixedRs = new PrefixedResultSet(rs, nested.getPropertyName() + ".");
                    Object nestedObj = nested.getDelegateMapper().mapRow(prefixedRs, rowNum);
                    
                    if (nestedObj != null) {
                        if (instance == null) {
                            instance = rootClass.getDeclaredConstructor().newInstance();
                        }
                        nested.getSetter().invoke(instance, nestedObj);
                    }
                } catch (SQLException e) {
                    // No columns with this prefix, skip
                }
            }
            
            return instance;
            
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to map row to " + rootClass.getSimpleName(), e);
        }
    }
    
    /**
     * Checks if a column exists in the ResultSet (doesn't throw SQLException).
     */
    private boolean columnExists(ResultSet rs, String columnName) {
        try {
            rs.findColumn(columnName);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}