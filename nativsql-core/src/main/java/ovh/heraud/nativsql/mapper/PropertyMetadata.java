package ovh.heraud.nativsql.mapper;

import ovh.heraud.nativsql.db.IdentifierConverter;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.TypeInfo;
import lombok.Getter;

/**
 * Metadata for a simple (non-nested) property.
 */
@Getter
public class PropertyMetadata<T> {
    private final FieldAccessor<?> fieldAccessor; // camelCase (e.g., "firstName")
    private final ITypeMapper<T> typeMapper;
    private final IdentifierConverter identifierConverter;
    private final TypeInfo typeInfo;

    public PropertyMetadata(FieldAccessor<?> fieldAccessor, ITypeMapper<T> typeMapper,
            IdentifierConverter identifierConverter, TypeInfo typeInfo) {
        this.fieldAccessor = fieldAccessor;
        this.typeMapper = typeMapper;
        this.identifierConverter = identifierConverter;
        this.typeInfo = typeInfo;
    }

    public String getColumnName() {
        return identifierConverter.toDB(fieldAccessor.getName());
    }
}