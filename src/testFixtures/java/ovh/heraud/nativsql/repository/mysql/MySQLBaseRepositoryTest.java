package ovh.heraud.nativsql.repository.mysql;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mysql.MySQLContainer;
import ovh.heraud.nativsql.mapper.RowMapperFactory;
import ovh.heraud.nativsql.repository.BaseRepositoryTest;

/**
 * Base class for repository integration tests using Testcontainers.
 * Provides common MySQL container setup and configuration.
 *
 * Initializes the schema once per JVM before any tests run.
 */
@SuppressWarnings("resource")
@SpringBootTest
@Import({ RowMapperFactory.class })
public abstract class MySQLBaseRepositoryTest extends BaseRepositoryTest {
    protected abstract String getScriptPath();

    static boolean mysqlSchemaLoaded = false;
    static JdbcDatabaseContainer<?> databaseContainer;

    @Override
    public JdbcDatabaseContainer<?> getDatabaseContainer() {
        return databaseContainer;
    }

    @Override
    protected boolean isSchemaLoaded() {
        return mysqlSchemaLoaded;
    }

    @Override
    protected void markSchemaAsLoaded() {
        mysqlSchemaLoaded = true;
    }

    protected static void init() {
        if (databaseContainer == null) {
            databaseContainer = new MySQLContainer("mysql:8.0")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");
            databaseContainer.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        init();

        // MySQL datasource
        registry.add("spring.datasource.mysql.url", databaseContainer::getJdbcUrl);
        registry.add("spring.datasource.mysql.username", databaseContainer::getUsername);
        registry.add("spring.datasource.mysql.password", databaseContainer::getPassword);
    }

}
