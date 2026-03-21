package ovh.heraud.nativsql.repository.postgres;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.mapper.RowMapperFactory;
import ovh.heraud.nativsql.repository.DbOperationLogger;
import ovh.heraud.nativsql.repository.IDataTypeTests;

/**
 * Integration tests for data type conversions using PostgreSQL.
 * Tests the matrix of type mappings between Java types and SQL types.
 */
@Import({ PostgresUserRepository.class, PostgresContactInfoRepository.class, PostgresGroupRepository.class })
class PostgresDataTypeTest extends PostgresRepositoryTest implements IDataTypeTests {

        @Autowired
        @Qualifier("pgDataSource")
        private DataSource dataSource;

        @Autowired
        @Qualifier("postgresDialect")
        private DatabaseDialect databaseDialect;

        @Autowired
        private RowMapperFactory rowMapperFactory;

        @Autowired
        private AnnotationManager annotationManager;

        @Autowired
        private DbOperationLogger dbOperationLogger;

        @Override
        public AnnotationManager getAnnotationManager() {
                return annotationManager;
        }

        @Override
        public DataSource getDataSource() {
                return dataSource;
        }

        @Override
        public DatabaseDialect getDatabaseDialect() {
                return databaseDialect;
        }

        @Override
        public RowMapperFactory getRowMapperFactory() {
                return rowMapperFactory;
        }

        @Override
        public DbOperationLogger getDbOperationLogger() {
                return dbOperationLogger;
        }

        @Test
        void testMarker() {
                // Empty method to ensure the test class is recognized by JUnit. Actual tests
                // are defined in the IDataTypeTests interface.
        }

        @Override
        public void testReadingByteArrayFromUUID() {
                // As postgres as a type for UUID. Conversion UUID <-> byte[] works.
                byte[] bytes1 = new byte[] { (byte) 0x55, (byte) 0x0e, (byte) 0x84, (byte) 0x00, (byte) 0xe2,
                                (byte) 0x9b, (byte) 0x41, (byte) 0xd4, (byte) 0xa7, (byte) 0x16, (byte) 0x44,
                                (byte) 0x66, (byte) 0x55, (byte) 0x44, (byte) 0x00, (byte) 0x00 };
                testReadingFromDB(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), bytes1);
        }

}
