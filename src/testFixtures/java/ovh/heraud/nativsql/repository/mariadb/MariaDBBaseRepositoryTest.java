package ovh.heraud.nativsql.repository.mariadb;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mariadb.MariaDBContainer;
import ovh.heraud.nativsql.mapper.RowMapperFactory;
import ovh.heraud.nativsql.repository.BaseRepositoryTest;

/**
 * Base class for repository integration tests using Testcontainers.
 * Provides common MariaDB container setup and configuration.
 *
 * Initializes the schema once per JVM before any tests run.
 */
@SuppressWarnings("resource")
@SpringBootTest
@Import({ RowMapperFactory.class })
public abstract class MariaDBBaseRepositoryTest extends BaseRepositoryTest {
    protected abstract String getScriptPath();

    static boolean mariadbSchemaLoaded = false;
    static JdbcDatabaseContainer<?> databaseContainer;

    @Override
    public JdbcDatabaseContainer<?> getDatabaseContainer() {
        return databaseContainer;
    }

    @Override
    protected boolean isSchemaLoaded() {
        return mariadbSchemaLoaded;
    }

    @Override
    protected void markSchemaAsLoaded() {
        mariadbSchemaLoaded = true;
    }

    protected static void init() {
        if (databaseContainer == null) {
            databaseContainer = new MariaDBContainer("mariadb:11.2")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");
            databaseContainer.start();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        init();

        // MariaDB datasource
        registry.add("spring.datasource.mariadb.url", databaseContainer::getJdbcUrl);
        registry.add("spring.datasource.mariadb.username", databaseContainer::getUsername);
        registry.add("spring.datasource.mariadb.password", databaseContainer::getPassword);
    }

}
