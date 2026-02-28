package ovh.heraud.nativsql.repository.mysql;

import javax.sql.DataSource;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.mysql.MySQLDialect;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.repository.GenericRepository;

public abstract class MySQLRepository<T extends IEntity<ID>, ID> extends GenericRepository<T, ID> {

    @Autowired
    @Qualifier("mySQLDataSource")
    @NonNull
    private DataSource mysqlDataSource;

    @Autowired
    @Qualifier("mySQLDialect")
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
}
