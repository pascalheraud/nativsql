package ovh.heraud.nativsql.config;

import javax.sql.DataSource;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.mariadb.MariaDBDialect;
import ovh.heraud.nativsql.db.mysql.MySQLDialect;
import ovh.heraud.nativsql.db.oracle.OracleDialect;

/**
 * Test configuration component for NativSQL dialects (MySQL, MariaDB, Oracle).
 * PostgreSQL config is in the nativsql-postgres module.
 */
@Component
@ComponentScan("ovh.heraud.nativsql")
public class TestNativSqlConfig {

    @Autowired
    private AnnotationManager annotationManager;

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

    @Bean(name = "oracleDialect")
    public OracleDialect oracleDialect() {
        OracleDialect dialect = new OracleDialect();

        // Configure types via AnnotationManager (annotations can be overridden here)
        annotationManager.setJsonInfo(ovh.heraud.nativsql.domain.oracle.Address.class);
        annotationManager.setJsonInfo(ovh.heraud.nativsql.domain.oracle.Preferences.class);

        return dialect;
    }

    /**
     * MySQL datasource configured via dynamic properties.
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
     * Oracle datasource configured via dynamic properties.
     */
    @Bean("oracleDataSource")
    public DataSource oracleDataSource(
            TestDataSourceProperties dataSourceProperties) {
        return DataSourceBuilder.create()
                .url(dataSourceProperties.getOracleUrl())
                .username(dataSourceProperties.getOracleUsername())
                .password(dataSourceProperties.getOraclePassword())
                .driverClassName("oracle.jdbc.OracleDriver")
                .build();
    }

    /**
     * PlatformTransactionManager for Oracle datasource.
     * This is required for @Transactional tests to work.
     */
    @Bean("oracleTransactionManager")
    public PlatformTransactionManager oracleTransactionManager(
            @Qualifier("oracleDataSource") @NonNull DataSource oracleDataSource) {
        return new DataSourceTransactionManager(oracleDataSource);
    }
}
