package ovh.heraud.nativsql.mapper;

import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.util.FieldAccessor;
import lombok.Getter;

/**
 * Metadata for a simple (non-nested) property.
 */
@Getter
public class PropertyMetadata<T> {
    private final FieldAccessor fieldAccessor; // camelCase (e.g., "firstName")
    private final ITypeMapper<T> typeMapper;
    private final IdentifierConverter identifierConverter;

    public PropertyMetadata(FieldAccessor fieldAccessor, ITypeMapper<T> typeMapper, IdentifierConverter identifierConverter) {
        this.fieldAccessor = fieldAccessor;
        this.typeMapper = typeMapper;
        this.identifierConverter = identifierConverter;
    }

    public String getColumnName() {
        return identifierConverter.toDB(fieldAccessor.getName());
    }
}