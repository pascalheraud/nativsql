package io.github.pascalheraud.nativsql.mapper;

import io.github.pascalheraud.nativsql.util.FieldAccessor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.support.JdbcUtils;

/**
 * Metadata for a simple (non-nested) property.
 */
@Getter
@RequiredArgsConstructor
public class PropertyMetadata<T> {
    private final FieldAccessor fieldAccessor; // camelCase (e.g., "firstName")
    private final ITypeMapper<T> typeMapper;

    public String getColumnName() {
        return JdbcUtils.convertPropertyNameToUnderscoreName(fieldAccessor.getName());
    }
}