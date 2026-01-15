package io.github.pascalheraud.nativsql.repository.mysql;

import javax.sql.DataSource;

import jakarta.annotation.Nonnull;

import io.github.pascalheraud.nativsql.db.DatabaseDialect;
import io.github.pascalheraud.nativsql.db.mysql.MySQLDialect;
import io.github.pascalheraud.nativsql.domain.Entity;
import io.github.pascalheraud.nativsql.repository.GenericRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class MySQLRepository<T extends Entity<ID>, ID> extends GenericRepository<T, ID> {

    @Autowired
    @Qualifier("mySQLDataSource")
    private DataSource mysqlDataSource;

    @Autowired
    private MySQLDialect mySQLDialect;

    @Override
    @Nonnull
    protected DataSource getDataSource() {
        return mysqlDataSource;
    }

    @Override
    protected DatabaseDialect getDatabaseDialectInstance() {
        return mySQLDialect;
    }
}
