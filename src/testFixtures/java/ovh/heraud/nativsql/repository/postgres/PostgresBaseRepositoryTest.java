package ovh.heraud.nativsql.repository.postgres;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import ovh.heraud.nativsql.repository.BaseRepositoryTest;

/**
 * Base class for PostgreSQL repository integration tests.
 * Each test creates its own container via @BeforeEach.
 */
@SuppressWarnings("resource")
public abstract class PostgresBaseRepositoryTest extends BaseRepositoryTest {
    protected JdbcDatabaseContainer<?> container;

    @Override
    protected String getDatabaseVersion() {
        return "15";
    }

    @Override
    protected String getDatabaseVendor() {
        return "postgres";
    }

    @Override
    protected JdbcDatabaseContainer<?> createContainer(String schemaHash) {
        String containerImage = "postgis/postgis:" + getDatabaseVersion() + "-3.3";
        DockerImageName postgisImage = DockerImageName
                .parse(containerImage)
                .asCompatibleSubstituteFor("postgres");

        return new PostgreSQLContainer(postgisImage)
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withLabel("schema.hash", schemaHash)
;
    }

}
