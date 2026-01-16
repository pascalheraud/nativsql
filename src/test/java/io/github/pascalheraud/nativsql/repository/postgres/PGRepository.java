package io.github.pascalheraud.nativsql.repository.postgres;

import javax.sql.DataSource;

import io.github.pascalheraud.nativsql.db.DatabaseDialect;
import io.github.pascalheraud.nativsql.db.postgres.PostgresDialect;
import io.github.pascalheraud.nativsql.domain.Entity;
import io.github.pascalheraud.nativsql.repository.GenericRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;

public abstract class PGRepository<T extends Entity<ID>, ID> extends GenericRepository<T, ID> {

    @Autowired
    @Qualifier("pgDataSource")
    @NonNull
    private DataSource pgDataSource;

    @Autowired
    private PostgresDialect postgresDialect;

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
