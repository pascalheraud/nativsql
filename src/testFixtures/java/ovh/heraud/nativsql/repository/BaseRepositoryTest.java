package ovh.heraud.nativsql.repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

import ovh.heraud.nativsql.config.NativSqlConfig;
import ovh.heraud.nativsql.mapper.RowMapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * Base class for repository integration tests using Testcontainers.
 * Provides common PostgreSQL container setup and configuration.
 *
 * Initializes the schema once per JVM before any tests run.
 */
@SpringBootTest
@Import({ NativSqlConfig.class, RowMapperFactory.class })
public abstract class BaseRepositoryTest {
    protected abstract String getScriptPath();

    protected abstract boolean isSchemaLoaded();

    protected abstract void markSchemaAsLoaded();

    public abstract JdbcDatabaseContainer<?> getDatabaseContainer();

    @BeforeEach
    private void loadSchema() throws SQLException, IOException {
        if (!isSchemaLoaded()) {
            markSchemaAsLoaded();
            try (Connection conn = DriverManager.getConnection(getDatabaseContainer().getJdbcUrl(),
                    getDatabaseContainer().getUsername(),
                    getDatabaseContainer().getPassword());
                    Statement stmt = conn.createStatement()) {

                String sql = readSqlScript();
                String[] statements = sql.split(";");
                for (String statement : statements) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }

            }
        }
    }

    private String readSqlScript() throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        BaseRepositoryTest.class.getClassLoader().getResourceAsStream(getScriptPath())))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
