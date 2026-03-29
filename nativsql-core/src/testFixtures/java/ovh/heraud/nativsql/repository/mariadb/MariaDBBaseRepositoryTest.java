package ovh.heraud.nativsql.repository.mariadb;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mariadb.MariaDBContainer;

import ovh.heraud.nativsql.repository.BaseRepositoryTest;

/**
 * Base class for MariaDB repository integration tests.
 * Each test creates its own container via @BeforeEach.
 */
@SuppressWarnings("resource")
public abstract class MariaDBBaseRepositoryTest extends BaseRepositoryTest {
    protected JdbcDatabaseContainer<?> container;

    @Override
    protected String getDatabaseVersion() {
        return "11.2";
    }

    @Override
    protected String getDatabaseVendor() {
        return "mariadb";
    }
    
    @Override
    protected JdbcDatabaseContainer<?> createContainer(String schemaHash) {
        return new MariaDBContainer("mariadb:" + getDatabaseVersion())
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withLabel("version", getDatabaseVersion())
                .withLabel("schema.hash", schemaHash)
;
    }

}
