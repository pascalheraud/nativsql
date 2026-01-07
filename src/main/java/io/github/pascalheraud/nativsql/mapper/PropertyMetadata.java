package io.github.pascalheraud.nativsql.mapper;

import java.lang.reflect.Method;

/**
 * Metadata for a simple (non-nested) property.
 */
public class PropertyMetadata {
    private final String propertyName;  // camelCase (e.g., "firstName")
    private final String columnName;    // snake_case (e.g., "first_name")
    private final Class<?> type;
    private final Method setter;
    private final TypeMapper<?> typeMapper;
    
    public PropertyMetadata(String propertyName, String columnName, Class<?> type, 
                           Method setter, TypeMapper<?> typeMapper) {
        this.propertyName = propertyName;
        this.columnName = columnName;
        this.type = type;
        this.setter = setter;
        this.typeMapper = typeMapper;
    }
    
    public String getPropertyName() {
        return propertyName;
    }
    
    public String getColumnName() {
        return columnName;
    }
    
    public Class<?> getType() {
        return type;
    }
    
    public Method getSetter() {
        return setter;
    }
    
    public TypeMapper<?> getTypeMapper() {
        return typeMapper;
    }
}