package io.github.pascalheraud.nativsql.repository;

import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import io.github.pascalheraud.nativsql.config.TestNativSqlConfig;

/**
 * Common base class for tests that need a test user.
 * Provides UserRepository and ContactInfoRepository beans and creates a test
 * user.
 * All test methods run in a transaction and are automatically rolled back after
 * each test.
 */
@Import({ TestNativSqlConfig.class})
@Transactional
public abstract class CommonUserTest extends BaseRepositoryTest {

    @Override
    protected DBType getDBType() {
        return DBType.POSTGRES;
    }
}
