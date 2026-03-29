package ovh.heraud.nativsql.repository.oracle;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.oracle.OracleContainer;

import ovh.heraud.nativsql.repository.BaseRepositoryTest;

/**
 * Base class for Oracle repository integration tests.
 * Each test creates its own container via @BeforeEach.
 */
@SuppressWarnings("resource")
public abstract class OracleBaseRepositoryTest extends BaseRepositoryTest {
    protected JdbcDatabaseContainer<?> container;

    @Override
    protected String getDatabaseVersion() {
        // Version using oracle-free, faster, lighter, stronger ;)
        return "23";
    }

    @Override
    protected String getDatabaseVendor() {
        return "oracle";
    }

    @Override
    protected JdbcDatabaseContainer<?> createContainer(String schemaHash) {
        if (Integer.valueOf(getDatabaseVersion())<23) {
            return (JdbcDatabaseContainer<?>) new org.testcontainers.containers.OracleContainer("gvenzl/oracle-xe:21")
                    .withDatabaseName("db1")
                    .withUsername("testuser")
                    .withPassword("testpass123")
                    .withLabel("version", getDatabaseVersion())
                    .withLabel("schema.hash", schemaHash);
        }
        return (JdbcDatabaseContainer<?>) new OracleContainer("gvenzl/oracle-free:" + getDatabaseVersion() + "-slim")
                .withDatabaseName("XEPDB1")
                .withUsername("testuser")
                .withPassword("testpass123")
                .withLabel("version", getDatabaseVersion())
                .withLabel("schema.hash", schemaHash);
    }
}
