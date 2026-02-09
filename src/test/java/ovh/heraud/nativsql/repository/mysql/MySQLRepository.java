package ovh.heraud.nativsql.repository.mysql;

import javax.sql.DataSource;

import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.mysql.MySQLDialect;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.repository.GenericRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.jspecify.annotations.NonNull;

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

    @Override
    protected Long getLastInsertedId() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(mysqlDataSource);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
