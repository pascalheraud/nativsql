package ovh.heraud.nativsql.db.mysql;

import java.util.Map;

import ovh.heraud.nativsql.db.AbstractChainedDialect;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.generic.GenericDialect;

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
 * Part of the Chain of Responsibility pattern, chains to GenericDialect for
 * unmapped types.
 * Can be extended to create specialized MySQL dialects (e.g., with spatial
 * Point support).
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

    @SuppressWarnings("unchecked")
    @Override
  public <ID> ID getGeneratedKey(Map<String, Object> keys, String idColumn) {
        return (ID) keys.get("GENERATED_KEY");
    }
}
