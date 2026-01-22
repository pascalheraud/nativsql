package ovh.heraud.nativsql.mapper;

import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.util.FieldAccessor;
import lombok.Getter;

/**
 * Metadata for a simple (non-nested) property.
 */
@Getter
public class PropertyMetadata<T> {
    private final FieldAccessor fieldAccessor; // camelCase (e.g., "firstName")
    private final ITypeMapper<T> typeMapper;
    private final DatabaseDialect dialect;

    public PropertyMetadata(FieldAccessor fieldAccessor, ITypeMapper<T> typeMapper, DatabaseDialect dialect) {
        this.fieldAccessor = fieldAccessor;
        this.typeMapper = typeMapper;
        this.dialect = dialect;
    }

    public String getColumnName() {
        return dialect.javaToDBIdentifier(fieldAccessor.getName());
    }
}