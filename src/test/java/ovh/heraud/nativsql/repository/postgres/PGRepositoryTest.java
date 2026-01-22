package ovh.heraud.nativsql.repository.postgres;

import ovh.heraud.nativsql.config.TestDataSourceProperties;
import ovh.heraud.nativsql.config.TestNativSqlConfig;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Common base class for PG tests .
 * All test methods run in a transaction and are automatically rolled back after
 * each test.
 */
@Import({ TestNativSqlConfig.class, TestDataSourceProperties.class })
@Transactional("pgTransactionManager")
public abstract class PGRepositoryTest extends PGBaseRepositoryTest {

    static {
        init();
    }

    protected String getScriptPath() {
        return "test-schema-postgres-init.sql";
    }
}
