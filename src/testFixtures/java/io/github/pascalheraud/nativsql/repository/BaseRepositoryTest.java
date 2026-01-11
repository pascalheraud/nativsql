package io.github.pascalheraud.nativsql.repository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.stream.Collectors;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.github.pascalheraud.nativsql.config.NativSqlConfig;
import io.github.pascalheraud.nativsql.mapper.RowMapperFactory;

/**
 * Base class for repository integration tests using Testcontainers.
 * Provides common PostgreSQL container setup and configuration.
 *
 * Initializes the schema once per JVM before any tests run.
 */
@SuppressWarnings("resource")
@SpringBootTest
@Import({ NativSqlConfig.class, RowMapperFactory.class })
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
        initializeSchema();
    }

    /**
     * Initializes the test schema once after the container starts.
     */
    private static void initializeSchema() {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
                Statement stmt = conn.createStatement()) {

            String sql = readSqlScript("test-schema-init.sql");
            stmt.execute(sql);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Schema init error", e);
        }
    }

    private static String readSqlScript(String scriptName) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(BaseRepositoryTest.class.getClassLoader().getResourceAsStream(scriptName)))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

}
