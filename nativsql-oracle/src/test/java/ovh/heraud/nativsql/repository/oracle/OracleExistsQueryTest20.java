package ovh.heraud.nativsql.repository.oracle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Integration tests for exists() / ExistsQuery on Oracle 20 (oracle-xe), mirroring
 * how {@link Oracle20DataTypeTest} overrides the database version for the
 * data-type suite. Oracle 20/23 differ enough (23c added native SQL BOOLEAN, JSON
 * improvements, ...) that a single version isn't sufficient proof the
 * "CASE WHEN EXISTS(...) THEN 1 ELSE 0 END FROM dual" shape holds on every
 * supported version.
 */
@Import({ OracleUserRepository.class })
class OracleExistsQueryTest20 extends OracleExistsQueryTestBase {

    @Autowired
    private OracleUserRepository userRepository;

    @Override
    protected OracleUserRepository getUserRepository() {
        return userRepository;
    }

    @Override
    protected String getDatabaseVersion() {
        return "20";
    }

    @Override
    protected String getScriptPath() {
        return "test-schema-oracle-20-init.sql";
    }
}
