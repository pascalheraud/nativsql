package ovh.heraud.nativsql.repository.mysql;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.mapper.RowMapperFactory;
import ovh.heraud.nativsql.repository.IDataTypeTests;

/**
 * Integration tests for data type conversions using MySQL.
 * Tests the matrix of type mappings between Java types and SQL types.
 */
@Import({ MySQLUserRepository.class, MySQLContactInfoRepository.class, MySQLGroupRepository.class })
class MySQLDataTypeTest extends MySQLRepositoryTest implements IDataTypeTests {

	@Autowired
	@Qualifier("mySQLDataSource")
	private DataSource dataSource;

	@Autowired
	@Qualifier("mySQLDialect")
	private DatabaseDialect databaseDialect;

	@Autowired
	private RowMapperFactory rowMapperFactory;

	@Autowired
	private AnnotationManager annotationManager;

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

	@Test
	void testMarker() {
		// Empty method to ensure the test class is recognized by JUnit. Actual tests
		// are defined in the IDataTypeTests interface.
	}
}
