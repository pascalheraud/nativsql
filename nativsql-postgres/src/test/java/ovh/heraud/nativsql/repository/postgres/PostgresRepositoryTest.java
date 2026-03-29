package ovh.heraud.nativsql.repository.postgres;

import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import ovh.heraud.nativsql.config.TestDataSourceProperties;
import ovh.heraud.nativsql.config.TestPostgresConfig;

/**
 * Common base class for PG tests .
 * All test methods run in a transaction and are automatically rolled back after
 * each test.
 */
@Import({ TestPostgresConfig.class, TestDataSourceProperties.class})
@Transactional("pgTransactionManager")

public abstract class PostgresRepositoryTest extends PostgresBaseRepositoryTest {
    protected String getScriptPath() {
        return "test-schema-postgres-init.sql";
    }
}
