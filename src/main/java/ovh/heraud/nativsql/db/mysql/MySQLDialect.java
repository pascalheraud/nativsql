package ovh.heraud.nativsql.db.mysql;

import ovh.heraud.nativsql.db.AbstractChainedDialect;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.generic.GenericDialect;
import ovh.heraud.nativsql.db.mysql.mapper.MySQLJSONTypeMapper;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * MySQL/MariaDB specific implementation of DatabaseDialect.
 *
 * Handles MySQL-specific SQL formatting and type conversions including:
 * - Enum types as strings (uses generic behavior)
 * - Composite types as JSON (uses generic behavior)
 * - JSON types (uses generic behavior)
 *
 * MySQL stores enums as VARCHAR and composites as JSON, which matches the
 * generic behavior.
 *
 * Part of the Chain of Responsibility pattern, chains to GenericDialect for unmapped types.
 * Can be extended to create specialized MySQL dialects (e.g., with spatial Point support).
 */
public class MySQLDialect extends AbstractChainedDialect {

    /**
     * Create a MySQL dialect that chains to the generic dialect.
     *
     * @param nextDialect the next dialect to delegate to
     */
    public MySQLDialect(DatabaseDialect nextDialect) {
        super(nextDialect);
    }

    /**
     * Create a MySQL dialect with a generic dialect as the next in chain.
     */
    public MySQLDialect() {
        super(new GenericDialect());
    }

    @Override
    public <T> ITypeMapper<T> getMapper(Class<T> targetType) {
        // Check for registered JSON types
        if (jsonTypes.containsKey(targetType)) {
            return getJsonMapper(targetType);
        }

        // Fall back to next dialect in chain for other types
        return super.getMapper(targetType);
    }

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
