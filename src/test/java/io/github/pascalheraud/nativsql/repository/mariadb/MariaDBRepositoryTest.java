package io.github.pascalheraud.nativsql.repository.mariadb;

import io.github.pascalheraud.nativsql.config.TestDataSourceProperties;
import io.github.pascalheraud.nativsql.config.TestNativSqlConfig;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Common base class for MariaDB tests.
 * All test methods run in a transaction and are automatically rolled back after
 * each test.
 */
@Import({ TestNativSqlConfig.class, TestDataSourceProperties.class })
@Transactional("mariaDBTransactionManager")
public abstract class MariaDBRepositoryTest extends MariaDBBaseRepositoryTest {
    static {
        init();
    }

    @Override
    protected String getScriptPath() {
        return "test-schema-mariadb-init.sql";
    }
}
