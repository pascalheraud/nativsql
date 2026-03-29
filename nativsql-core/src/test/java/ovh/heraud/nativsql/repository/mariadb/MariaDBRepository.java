package ovh.heraud.nativsql.repository.mariadb;

import javax.sql.DataSource;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.mariadb.MariaDBDialect;
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

    @Autowired(required = false)
    @Qualifier("mariaDBDataSource")
    @NonNull
    private DataSource mariadbDataSource;

    /**
     * Set DataSource for tests. Called by test framework via reflection.
     */
    void setDataSourceForTest(DataSource dataSource) {
        this.mariadbDataSource = dataSource;
    }

    @Autowired()
    @Qualifier("mariaDBDialect")
    private MariaDBDialect mariadbDialect;

    @Override
    @NonNull
    protected DataSource getDataSource() {
        return mariadbDataSource;
    }

    @Override
    protected DatabaseDialect getDatabaseDialectInstance() {
        return mariadbDialect;
    }
}
