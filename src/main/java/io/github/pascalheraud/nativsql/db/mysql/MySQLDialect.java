package io.github.pascalheraud.nativsql.db.mysql;

import io.github.pascalheraud.nativsql.db.DefaultDialect;
import io.github.pascalheraud.nativsql.mapper.ITypeMapper;
import org.springframework.stereotype.Component;

/**
 * MySQL-specific implementation of DatabaseDialect.
 *
 * Handles MySQL-specific SQL formatting and type conversions including:
 * - Enum types as strings (uses default behavior)
 * - Composite types as JSON (uses default behavior)
 * - JSON types (uses default behavior)
 *
 * MySQL stores enums as VARCHAR and composites as JSON, which matches the
 * default behavior.
 * The main override is the SQL formatting - MySQL doesn't need type casting for
 * parameters.
 */
@Component
public class MySQLDialect extends DefaultDialect {


    @Override
    public <T> ITypeMapper<T> getJsonMapper(Class<T> jsonClass) {
        // MySQL uses the dedicated MySQLJSONTypeMapper
        return new MySQLJSONTypeMapper<>(jsonClass);
    }

    @Override
    public <T> ITypeMapper<T> getCompositeMapper(Class<T> compositeClass) {
        // MySQL does not support native composite types like PostgreSQL
        throw new UnsupportedOperationException(
                "MySQL does not support native composite types. " +
                        "Use JSON columns instead for composite data: " + compositeClass.getSimpleName());
    }
}
