package ovh.heraud.nativsql.repository.oracle;

import java.time.LocalDate;

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
 * Integration tests for data type conversions using Oracle.
 * Tests the matrix of type mappings between Java types and SQL types.
 */
@Import({ OracleUserRepository.class, OracleContactInfoRepository.class, OracleGroupRepository.class })
class Oracle20DataTypeTest extends OracleRepositoryTest implements IDataTypeTests {

	@Autowired
	@Qualifier("oracleDialect")
	private DatabaseDialect databaseDialect;

	@Override
	protected String getDatabaseVersion() {
		return "20";
	}

	@Override
	protected String getScriptPath() {
		return "test-schema-oracle-20-init.sql";
	}

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
	public void testReadingStringFromBoolean() {
		// We disable this test for Oracle 20 because the BOOLEAN type is not supported
		// in Oracle 20, thus there is no point reading a boolean ;)
	}
	
	@Override
	public void testReadingStringFromDate() {
		// Specific format for Oracle
		testReadingFromDB(java.sql.Date.valueOf(LocalDate.of(2024, 1, 15)), "2024-01-15T12:00");
	}
}
