package ovh.heraud.nativsql.db.mariadb;

import java.util.Map;

import ovh.heraud.nativsql.db.mysql.MySQLDialect;

/**
 * MariaDB specific implementation of DatabaseDialect.
 *
 * Extends MySQLDialect since MariaDB is MySQL-compatible, but overrides
 * getGeneratedKey to use MariaDB's "insert_id" field name instead of
 * MySQL's "GENERATED_KEY".
 */
public class MariaDBDialect extends MySQLDialect {

    /**
     * Create a MariaDB dialect.
     */
    public MariaDBDialect() {
        super();
    }

    @Override
    public <ID> ID getGeneratedKey(Map<String, Object> keys, String idColumn) {
        return (ID) keys.get("insert_id");
    }
}
