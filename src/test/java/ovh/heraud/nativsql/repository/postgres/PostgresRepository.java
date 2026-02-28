package ovh.heraud.nativsql.repository.postgres;

import javax.sql.DataSource;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.repository.GenericRepository;

public abstract class PostgresRepository<T extends IEntity<ID>, ID> extends GenericRepository<T, ID> {

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
}
