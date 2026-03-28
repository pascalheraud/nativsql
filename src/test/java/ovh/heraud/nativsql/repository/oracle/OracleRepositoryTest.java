package ovh.heraud.nativsql.repository.oracle;

import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import ovh.heraud.nativsql.config.TestDataSourceProperties;
import ovh.heraud.nativsql.config.TestNativSqlConfig;

/**
 * Common base class for Oracle tests.
 * All test methods run in a transaction and are automatically rolled back after
 * each test.
 */
@Import({ TestNativSqlConfig.class, TestDataSourceProperties.class })
@Transactional("oracleTransactionManager")
public abstract class OracleRepositoryTest extends OracleBaseRepositoryTest {

    @Override
    protected String getScriptPath() {
        return "test-schema-oracle-init.sql";
    }
}
