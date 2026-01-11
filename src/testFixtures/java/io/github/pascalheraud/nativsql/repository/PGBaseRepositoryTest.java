package io.github.pascalheraud.nativsql.repository;

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
@SpringBootTest
@Import({ NativSqlConfig.class, RowMapperFactory.class })
public abstract class PGBaseRepositoryTest {
    protected abstract String getScriptPath();

    static JdbcDatabaseContainer<?> databaseContainer;
    // static JdbcDatabaseContainer<?> secondaryDatabaseContainer;
    static boolean loadSchema = false;

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

    @BeforeEach
    private void loadSchema() throws SQLException, IOException {
        if (!loadSchema) {
            loadSchema = true;
            try (Connection conn = DriverManager.getConnection(databaseContainer.getJdbcUrl(),
                    databaseContainer.getUsername(),
                    databaseContainer.getPassword());
                    Statement stmt = conn.createStatement()) {

                String sql = readSqlScript();
                stmt.execute(sql);

            }
        }
    }

    private String readSqlScript() throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        PGBaseRepositoryTest.class.getClassLoader().getResourceAsStream(getScriptPath())))) {
            return reader.lines().collect(Collectors.joining("\n"));
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
