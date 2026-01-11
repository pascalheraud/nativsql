package io.github.pascalheraud.nativsql.repository.postgres;

import javax.sql.DataSource;

import io.github.pascalheraud.nativsql.domain.Entity;
import io.github.pascalheraud.nativsql.repository.GenericRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class PGRepository<T extends Entity<ID>, ID> extends GenericRepository<T, ID> {

    @Autowired
    @Qualifier("pgDataSource")
    private DataSource pgDataSource;

    @Override
    protected DataSource getDataSource() {
        return pgDataSource;
    }
}
