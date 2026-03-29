package ovh.heraud.nativsql.repository.oracle;

import org.springframework.context.annotation.Import;

import ovh.heraud.nativsql.config.TestOracleConfig;

/**
 * Common base class for Oracle tests.
 * All test methods run in a transaction and are automatically rolled back after
 * each test.
 */
@Import({ TestOracleConfig.class })
public abstract class OracleRepositoryTest extends OracleBaseRepositoryTest {

    @Override
    protected String getScriptPath() {
        return "test-schema-oracle-init.sql";
    }
}
