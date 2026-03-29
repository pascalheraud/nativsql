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
import ovh.heraud.nativsql.db.postgres.PostgresDialect;
import ovh.heraud.nativsql.db.postgres.postgis.PostgresPostGISDialect;
import ovh.heraud.nativsql.domain.postgres.Address;
import ovh.heraud.nativsql.domain.postgres.Preferences;
import ovh.heraud.nativsql.domain.postgres.UserStatus;

/**
 * Test configuration for PostgreSQL dialect.
 */
@Component
@ComponentScan("ovh.heraud.nativsql")
public class TestPostgresConfig {

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

    /**
     * PostgreSQL datasource configured via dynamic properties.
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
     * PlatformTransactionManager for PostgreSQL datasource.
     * This is required for @Transactional tests to work.
     */
    @Bean("pgTransactionManager")
    public PlatformTransactionManager pgTransactionManager(
            @Qualifier("pgDataSource") @NonNull DataSource pgDataSource) {
        return new DataSourceTransactionManager(pgDataSource);
    }
}
