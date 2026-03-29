package ovh.heraud.nativsql.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.mysql.MySQLDialect;
import ovh.heraud.nativsql.domain.mysql.Address;
import ovh.heraud.nativsql.domain.mysql.Preferences;

/**
 * Test configuration component for MySQL dialect.
 */
@Component
@ComponentScan("ovh.heraud.nativsql")
public class TestMySQLConfig {

    @Autowired
    private AnnotationManager annotationManager;

    @Bean
    public MySQLDialect mySQLDialect() {
        MySQLDialect dialect = new MySQLDialect();

        // Configure types via AnnotationManager (annotations can be overridden here)
        annotationManager.setJsonInfo(Address.class);
        annotationManager.setJsonInfo(Preferences.class);

        return dialect;
    }
}
