package io.github.pascalheraud.nativsql.repository;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for repository integration tests using Testcontainers.
 * Provides common PostgreSQL container setup and configuration.
 *
 * Subclasses must use @Sql annotation to specify their initialization script:
 * @Sql(scripts = "/test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
 */
@SuppressWarnings("resource")
@SpringBootTest
public abstract class BaseRepositoryTest {

    /**
     * Returns the database type to use for testing.
     *
     * @return the database type
     */
    protected abstract DBType getDBType();

    static DockerImageName postgisImage = DockerImageName
            .parse("postgis/postgis:15-3.3")
            .asCompatibleSubstituteFor("postgres");

    static PostgreSQLContainer postgres;

    static {
        postgres = new PostgreSQLContainer(postgisImage)
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanupDatabase() {
        jdbcTemplate.getJdbcTemplate().execute("TRUNCATE users CASCADE");
    }
}
