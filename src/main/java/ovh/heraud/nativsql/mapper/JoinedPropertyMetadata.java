package ovh.heraud.nativsql.mapper;

import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * Metadata for a joined (nested object) property.
 * Used to map columns from a joined table to a nested object property.
 */
public class JoinedPropertyMetadata {
    private final FieldAccessor fieldAccessor;
    private final GenericRowMapper<?> delegateMapper;

    public JoinedPropertyMetadata(FieldAccessor fieldAccessor, GenericRowMapper<?> delegateMapper) {
        this.fieldAccessor = fieldAccessor;
        this.delegateMapper = delegateMapper;
    }

    public String getPropertyName() {
        return fieldAccessor.getName();
    }

    public Class<?> getPropertyType() {
        return fieldAccessor.getType();
    }

    public FieldAccessor getFieldAccessor() {
        return fieldAccessor;
    }

    public GenericRowMapper<?> getDelegateMapper() {
        return delegateMapper;
    }
}
