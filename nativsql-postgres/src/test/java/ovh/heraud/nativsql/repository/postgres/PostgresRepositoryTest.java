package ovh.heraud.nativsql.repository.postgres;

import org.springframework.context.annotation.Import;

import ovh.heraud.nativsql.config.TestPostgresConfig;

/**
 * Common base class for PG tests .
 */
@Import({ TestPostgresConfig.class })

public abstract class PostgresRepositoryTest extends PostgresBaseRepositoryTest {
  protected String getScriptPath() {
    return "test-schema-postgres-init.sql";
  }
}
