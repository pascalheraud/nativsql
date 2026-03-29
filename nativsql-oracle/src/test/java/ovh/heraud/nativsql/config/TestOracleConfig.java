package ovh.heraud.nativsql.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.oracle.OracleDialect;
import ovh.heraud.nativsql.domain.oracle.Address;
import ovh.heraud.nativsql.domain.oracle.Preferences;

/**
 * Test configuration for Oracle dialect.
 */
@Component
@ComponentScan("ovh.heraud.nativsql")
public class TestOracleConfig {

    @Autowired
    private AnnotationManager annotationManager;

    @Bean
    public OracleDialect oracleDialect() {
        OracleDialect dialect = new OracleDialect();

        // Configure types via AnnotationManager (annotations can be overridden here)
        annotationManager.setJsonInfo(Address.class);
        annotationManager.setJsonInfo(Preferences.class);

        return dialect;
    }
}
