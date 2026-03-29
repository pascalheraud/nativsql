package ovh.heraud.nativsql.repository.mysql;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;

import ovh.heraud.nativsql.repository.BaseRepositoryTest;

/**
 * Base class for MySQL repository integration tests.
 * Each test creates its own container via @BeforeEach.
 */
@SuppressWarnings("resource")
public abstract class MySQLBaseRepositoryTest extends BaseRepositoryTest {
    protected JdbcDatabaseContainer<?> container;

    @Override
    protected String getDatabaseVersion() {
        return "8.0";
    }

    @Override
    protected String getDatabaseVendor() {
        return "mysql";
    }

    @Override
    protected JdbcDatabaseContainer<?> createContainer(String schemaHash) {
        return (JdbcDatabaseContainer<?>) new MySQLContainer("mysql:" + getDatabaseVersion())
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass123")
                .withLabel("version", getDatabaseVersion())
                .withLabel("schema.hash", schemaHash);
    }
}
