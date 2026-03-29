package ovh.heraud.nativsql.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.mariadb.MariaDBDialect;
import ovh.heraud.nativsql.domain.mariadb.Address;
import ovh.heraud.nativsql.domain.mariadb.Preferences;

/**
 * Test configuration component for MariaDB dialect.
 */
@Component
@ComponentScan("ovh.heraud.nativsql")
public class TestMariaDBConfig {

    @Autowired
    private AnnotationManager annotationManager;

    @Bean
    public MariaDBDialect mariaDBDialect() {
        MariaDBDialect dialect = new MariaDBDialect();

        // Configure types via AnnotationManager (annotations can be overridden here)
        annotationManager.setJsonInfo(Address.class);
        annotationManager.setJsonInfo(Preferences.class);

        return dialect;
    }
}
