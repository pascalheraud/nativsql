package ovh.heraud.nativsql.repository.mariadb;

import javax.sql.DataSource;

import ovh.heraud.nativsql.db.mariadb.MariaDBDialect;
import ovh.heraud.nativsql.domain.Entity;
import ovh.heraud.nativsql.repository.GenericRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;

/**
 * Base repository for MariaDB.
 * All MariaDB repositories extend this class.
 *
 * @param <T> the entity type
 * @param <ID> the id type
 */
public abstract class MariaDBRepository<T extends Entity<ID>, ID> extends GenericRepository<T, ID> {

    @Autowired
    @Qualifier("mariaDBDataSource")
    @NonNull
    private DataSource mariadbDataSource;

    @Autowired
    private MariaDBDialect mariadbDialect;

    @Override
    @NonNull
    protected DataSource getDataSource() {
        return mariadbDataSource;
    }

    @Override
    protected ovh.heraud.nativsql.db.DatabaseDialect getDatabaseDialectInstance() {
        return mariadbDialect;
    }

    @Override
    protected Long getLastInsertedId() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(mariadbDataSource);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
