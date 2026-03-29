package ovh.heraud.nativsql.repository.oracle;

import javax.sql.DataSource;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.oracle.OracleDialect;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.repository.GenericRepository;

public abstract class OracleRepository<T extends IEntity<ID>, ID> extends GenericRepository<T, ID> {

    @Autowired(required = false)
    @Qualifier("oracleDataSource")
    @NonNull
    private DataSource oracleDataSource;

    @Autowired
    @Qualifier("oracleDialect")
    private OracleDialect oracleDialect;

    @Override
    @NonNull
    protected DataSource getDataSource() {
        return oracleDataSource;
    }

    @Override
    protected DatabaseDialect getDatabaseDialectInstance() {
        return oracleDialect;
    }

    /**
     * Set DataSource for tests. Called by test framework via reflection.
     */
    void setDataSourceForTest(DataSource dataSource) {
        this.oracleDataSource = dataSource;
    }
}
