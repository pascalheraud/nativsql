package ovh.heraud.nativsql.config;

import javax.sql.DataSource;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.mariadb.MariaDBDialect;
import ovh.heraud.nativsql.db.mysql.MySQLDialect;
import ovh.heraud.nativsql.db.postgres.PostgresDialect;
import ovh.heraud.nativsql.db.postgres.postgis.PostgresPostGISDialect;
import ovh.heraud.nativsql.domain.postgres.Address;
import ovh.heraud.nativsql.domain.postgres.Preferences;
import ovh.heraud.nativsql.domain.postgres.UserStatus;

/**
 * Test configuration component for NativSQL dialects.
 *
 * <p>
 * Domain types are registered programmatically via dialect registration methods.
 * Annotations (@Json, @CompositeType, @EnumMapping) are automatically detected by dialects
 * and can be overridden with explicit programmatic registration here.
 * </p>
 */
@Component
public class TestNativSqlConfig {

    @Autowired
    private AnnotationManager annotationManager;

    @Bean
    public PostgresPostGISDialect postgresDialect() {
        PostgresDialect baseDialect = new PostgresDialect();
        PostgresPostGISDialect postgisDialect = new PostgresPostGISDialect(baseDialect);

        // Configure types via AnnotationManager (annotations can be overridden here)
        annotationManager.setCompositeTypeInfo(Address.class, "address_type");
        annotationManager.setJsonInfo(Preferences.class);
        annotationManager.setEnumMappingInfo(UserStatus.class, "user_status");

        return postgisDialect;
    }

    @Bean(name = "mySQLDialect")
    public MySQLDialect mySQLDialect() {
        MySQLDialect dialect = new MySQLDialect();

        // Configure types via AnnotationManager (annotations can be overridden here)
        annotationManager.setJsonInfo(ovh.heraud.nativsql.domain.mysql.Address.class);
        annotationManager.setJsonInfo(ovh.heraud.nativsql.domain.mysql.Preferences.class);

        return dialect;
    }

    @Bean(name = "mariaDBDialect")
    public MariaDBDialect mariaDBDialect() {
        MariaDBDialect dialect = new MariaDBDialect();

        // Configure types via AnnotationManager (annotations can be overridden here)
        annotationManager.setJsonInfo(ovh.heraud.nativsql.domain.mariadb.Address.class);
        annotationManager.setJsonInfo(ovh.heraud.nativsql.domain.mariadb.Preferences.class);

        return dialect;
    }

    /**
     * Primary datasource configured via dynamic properties.
     */
    @Bean("pgDataSource")
    public DataSource pgDataSource(
            TestDataSourceProperties dataSourceProperties) {
        return DataSourceBuilder.create()
                .url(dataSourceProperties.getPgUrl())
                .username(dataSourceProperties.getPgUsername())
                .password(dataSourceProperties.getPgPassword())
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    /**
     * Primary datasource configured via dynamic properties.
     */
    @Bean("mySQLDataSource")
    public DataSource mysqlDataSource(
            TestDataSourceProperties dataSourceProperties) {
        return DataSourceBuilder.create()
                .url(dataSourceProperties.getMySQLUrl())
                .username(dataSourceProperties.getMySQLUsername())
                .password(dataSourceProperties.getMySQLPassword())
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .build();
    }

    /**
     * PlatformTransactionManager for MySQL datasource.
     * This is required for @Transactional tests to work.
     */
    @Bean("mySQLTransactionManager")
    public PlatformTransactionManager mySQLtransactionManager(
            @Qualifier("mySQLDataSource") @NonNull DataSource mySQLDataSource) {
        return new DataSourceTransactionManager(mySQLDataSource);
    }

    /**
     * MariaDB datasource configured via dynamic properties.
     */
    @Bean("mariaDBDataSource")
    public DataSource mariaDBDataSource(
            TestDataSourceProperties dataSourceProperties) {
        return DataSourceBuilder.create()
                .url(dataSourceProperties.getMariaDBUrl())
                .username(dataSourceProperties.getMariaDBUsername())
                .password(dataSourceProperties.getMariaDBPassword())
                .driverClassName("org.mariadb.jdbc.Driver")
                .build();
    }

    /**
     * PlatformTransactionManager for MariaDB datasource.
     * This is required for @Transactional tests to work.
     */
    @Bean("mariaDBTransactionManager")
    public PlatformTransactionManager mariaDBTransactionManager(
            @Qualifier("mariaDBDataSource") @NonNull DataSource mariaDBDataSource) {
        return new DataSourceTransactionManager(mariaDBDataSource);
    }

    /**
     * PlatformTransactionManager for PostgreSQL datasource.
     * This is required for @Transactional tests to work.
     */
    @Bean("pgTransactionManager")
    public PlatformTransactionManager pgTransactionManager(
            @Qualifier("pgDataSource") @NonNull DataSource pgDataSource) {
        return new DataSourceTransactionManager(pgDataSource);
    }
}
