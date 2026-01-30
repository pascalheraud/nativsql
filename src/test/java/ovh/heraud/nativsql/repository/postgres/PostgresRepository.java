package ovh.heraud.nativsql.repository.postgres;

import javax.sql.DataSource;

import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.domain.Entity;
import ovh.heraud.nativsql.repository.GenericRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.jspecify.annotations.NonNull;

public abstract class PostgresRepository<T extends Entity<ID>, ID> extends GenericRepository<T, ID> {

    @Autowired
    @Qualifier("pgDataSource")
    @NonNull
    private DataSource pgDataSource;

    @Autowired
    private DatabaseDialect postgresDialect;

    @Override
    @NonNull
    protected DataSource getDataSource() {
        return pgDataSource;
    }

    @Override
    protected DatabaseDialect getDatabaseDialectInstance() {
        return postgresDialect;
    }

    @Override
    protected Long getLastInsertedId() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(pgDataSource);
        return jdbcTemplate.queryForObject("SELECT lastval()", Long.class);
    }

}
