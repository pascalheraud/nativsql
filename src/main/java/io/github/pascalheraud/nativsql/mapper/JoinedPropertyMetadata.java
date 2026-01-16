package io.github.pascalheraud.nativsql.mapper;

import io.github.pascalheraud.nativsql.util.FieldAccessor;

/**
 * Metadata for a joined (nested object) property.
 * Used to map columns from a joined table to a nested object property.
 */
public class JoinedPropertyMetadata {
    private final String propertyName;  // camelCase (e.g., "group")
    private final Class<?> propertyType;
    private final FieldAccessor fieldAccessor;
    private final GenericRowMapper<?> delegateMapper;
    private final String joinTableName;  // The name of the joined table

    public JoinedPropertyMetadata(String propertyName, Class<?> propertyType,
                                  FieldAccessor fieldAccessor, GenericRowMapper<?> delegateMapper,
                                  String joinTableName) {
        this.propertyName = propertyName;
        this.propertyType = propertyType;
        this.fieldAccessor = fieldAccessor;
        this.delegateMapper = delegateMapper;
        this.joinTableName = joinTableName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public Class<?> getPropertyType() {
        return propertyType;
    }

    public FieldAccessor getFieldAccessor() {
        return fieldAccessor;
    }

    public GenericRowMapper<?> getDelegateMapper() {
        return delegateMapper;
    }

    public String getJoinTableName() {
        return joinTableName;
    }
}
