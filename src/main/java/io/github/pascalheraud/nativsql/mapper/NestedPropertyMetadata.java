package io.github.pascalheraud.nativsql.mapper;

import java.lang.reflect.Method;

/**
 * Metadata for a nested (object) property.
 */
public class NestedPropertyMetadata {
    private final String propertyName;  // camelCase (e.g., "address")
    private final Class<?> propertyType;
    private final Method setter;
    private final GenericRowMapper<?> delegateMapper;
    
    public NestedPropertyMetadata(String propertyName, Class<?> propertyType,
                                  Method setter, GenericRowMapper<?> delegateMapper) {
        this.propertyName = propertyName;
        this.propertyType = propertyType;
        this.setter = setter;
        this.delegateMapper = delegateMapper;
    }
    
    public String getPropertyName() {
        return propertyName;
    }
    
    public Class<?> getPropertyType() {
        return propertyType;
    }
    
    public Method getSetter() {
        return setter;
    }
    
    public GenericRowMapper<?> getDelegateMapper() {
        return delegateMapper;
    }
}