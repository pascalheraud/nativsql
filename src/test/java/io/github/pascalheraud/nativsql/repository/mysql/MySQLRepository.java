package io.github.pascalheraud.nativsql.repository.mysql;

import javax.sql.DataSource;

import io.github.pascalheraud.nativsql.db.DatabaseDialect;
import io.github.pascalheraud.nativsql.db.mysql.MySQLDialect;
import io.github.pascalheraud.nativsql.domain.Entity;
import io.github.pascalheraud.nativsql.repository.GenericRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;

public abstract class MySQLRepository<T extends Entity<ID>, ID> extends GenericRepository<T, ID> {

    @Autowired
    @Qualifier("mySQLDataSource")
    @NonNull
    private DataSource mysqlDataSource;

    @Autowired
    private MySQLDialect mySQLDialect;

    @Override
    @NonNull
    protected DataSource getDataSource() {
        return mysqlDataSource;
    }

    @Override
    protected DatabaseDialect getDatabaseDialectInstance() {
        return mySQLDialect;
    }

    @Override
    protected Long getLastInsertedId() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(mysqlDataSource);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
