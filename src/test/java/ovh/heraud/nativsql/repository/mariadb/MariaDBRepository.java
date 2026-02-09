package ovh.heraud.nativsql.repository.mariadb;

import javax.sql.DataSource;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.mysql.MySQLDialect;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.repository.GenericRepository;

/**
 * Base repository for MariaDB.
 * All MariaDB repositories extend this class.
 *
 * @param <T>  the entity type
 * @param <ID> the id type
 */
public abstract class MariaDBRepository<T extends IEntity<ID>, ID> extends GenericRepository<T, ID> {

    @Autowired
    @Qualifier("mariaDBDataSource")
    @NonNull
    private DataSource mariadbDataSource;

    @Autowired()
    @Qualifier("mariaDBDialect")
    private MySQLDialect mariadbDialect;

    @Override
    @NonNull
    protected DataSource getDataSource() {
        return mariadbDataSource;
    }

    @Override
    protected DatabaseDialect getDatabaseDialectInstance() {
        return mariadbDialect;
    }

    @Override
    protected Long getLastInsertedId() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(mariadbDataSource);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
