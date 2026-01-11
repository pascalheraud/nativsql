package io.github.pascalheraud.nativsql.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;

import io.github.pascalheraud.nativsql.config.NativSqlConfig;
import io.github.pascalheraud.nativsql.config.TestNativSqlConfig;
import io.github.pascalheraud.nativsql.mapper.RowMapperFactory;

/**
 * Common base class for tests that need a test user.
 * Provides UserRepository and ContactInfoRepository beans and creates a test user.
 */
@Import({ NativSqlConfig.class, TestNativSqlConfig.class, RowMapperFactory.class,
          UserRepository.class, ContactInfoRepository.class })
@Sql(scripts = "/test-schema-cleanup.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
public abstract class CommonUserTest extends BaseRepositoryTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ContactInfoRepository contactInfoRepository;

    @Override
    protected DBType getDBType() {
        return DBType.POSTGRES;
    }
}
