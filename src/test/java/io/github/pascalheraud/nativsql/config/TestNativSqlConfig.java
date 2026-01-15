package io.github.pascalheraud.nativsql.config;

import javax.sql.DataSource;

import jakarta.annotation.PostConstruct;

import io.github.pascalheraud.nativsql.db.mysql.MySQLDialect;
import io.github.pascalheraud.nativsql.db.postgres.PostgresDialect;
import io.github.pascalheraud.nativsql.domain.postgres.Address;
import io.github.pascalheraud.nativsql.domain.postgres.Preferences;
import io.github.pascalheraud.nativsql.domain.postgres.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Test configuration component that registers example domain types for testing.
 *
 * <p>
 * This implementation of {@link INativSQLConfiguration} demonstrates how
 * applications
 * should configure NativSQL by implementing the interface and providing type
 * mappings.
 * </p>
 */
@Component
public class TestNativSqlConfig {

    @Autowired
    private PostgresDialect postgresDialect;

    @Autowired
    private MySQLDialect mysqlDialect;

    /**
     * Configures NativSQL with test domain types.
     */
    @PostConstruct
    public void configure() {
        configurePG();
        configureMySQL();
    }

    private void configureMySQL() {
        // MySQL - Register JSON types
        mysqlDialect.registerJsonType(io.github.pascalheraud.nativsql.domain.mysql.Address.class);
        mysqlDialect.registerJsonType(io.github.pascalheraud.nativsql.domain.mysql.Preferences.class);
    }

    private void configurePG() {
        // PostgreSQL - Register composite and enum types
        postgresDialect.registerCompositeType(Address.class, "address_type");
        postgresDialect.registerJsonType(Preferences.class);
        postgresDialect.registerEnumType(UserStatus.class, "user_status");
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
            @Qualifier("mySQLDataSource") DataSource mySQLDataSource) {
        return new DataSourceTransactionManager(mySQLDataSource);
    }

    /**
     * PlatformTransactionManager for MySQL datasource.
     * This is required for @Transactional tests to work.
     */
    @Bean("pgTransactionManager")
    public PlatformTransactionManager pgTransactionManager(
            @Qualifier("pgDataSource") DataSource pgDataSource) {
        return new DataSourceTransactionManager(pgDataSource);
    }
}
