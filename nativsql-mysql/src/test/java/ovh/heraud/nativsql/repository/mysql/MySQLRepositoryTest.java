package ovh.heraud.nativsql.repository.mysql;

import ovh.heraud.nativsql.config.TestMySQLConfig;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Common base class for MySQL tests.
 * All test methods run in a transaction and are automatically rolled back after
 * each test.
 */
@Import({ TestMySQLConfig.class })
@Transactional("mySQLTransactionManager")
public abstract class MySQLRepositoryTest extends MySQLBaseRepositoryTest {
    @Override
    protected String getScriptPath() {
        return "test-schema-mysql-init.sql";
    }
}
