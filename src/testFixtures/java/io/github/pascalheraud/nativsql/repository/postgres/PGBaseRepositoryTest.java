package io.github.pascalheraud.nativsql.repository.postgres;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

import io.github.pascalheraud.nativsql.config.NativSqlConfig;
import io.github.pascalheraud.nativsql.mapper.RowMapperFactory;
import io.github.pascalheraud.nativsql.repository.BaseRepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for repository integration tests using Testcontainers.
 * Provides common PostgreSQL container setup and configuration.
 *
 * Initializes the schema once per JVM before any tests run.
 */
@SuppressWarnings("resource")
public abstract class PGBaseRepositoryTest extends BaseRepositoryTest{
    protected abstract String getScriptPath();

    static boolean pgSchemaLoaded = false;
    static JdbcDatabaseContainer<?> databaseContainer;

    @Override
    public JdbcDatabaseContainer<?> getDatabaseContainer() {
        return databaseContainer;
    }

    @Override
    protected boolean isSchemaLoaded() {
        return pgSchemaLoaded;
    }

    @Override
    protected void markSchemaAsLoaded() {
        pgSchemaLoaded = true;
    }

    protected static void init() {
        if (databaseContainer == null) {
            DockerImageName postgisImage = DockerImageName
                    .parse("postgis/postgis:15-3.3")
                    .asCompatibleSubstituteFor("postgres");

            databaseContainer = new PostgreSQLContainer(postgisImage)
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");
            databaseContainer.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        init();

        // Primary datasource
        registry.add("spring.datasource.pg.url", databaseContainer::getJdbcUrl);
        registry.add("spring.datasource.pg.username", databaseContainer::getUsername);
        registry.add("spring.datasource.pg.password", databaseContainer::getPassword);
    }

}
