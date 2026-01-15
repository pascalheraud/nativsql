package io.github.pascalheraud.nativsql.repository.mysql;

import io.github.pascalheraud.nativsql.config.TestDataSourceProperties;
import io.github.pascalheraud.nativsql.config.TestNativSqlConfig;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Common base class for MySQL tests.
 * All test methods run in a transaction and are automatically rolled back after
 * each test.
 */
@Import({ TestNativSqlConfig.class, TestDataSourceProperties.class })
@Transactional("mySQLTransactionManager")
public abstract class MySQLRepositoryTest extends MySQLBaseRepositoryTest {
    static {
        init();
    }

    @Override
    protected String getScriptPath() {
        return "test-schema-mysql-init.sql";
    }
}
