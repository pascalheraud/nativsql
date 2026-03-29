package ovh.heraud.nativsql.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.mariadb.MariaDBDialect;
import ovh.heraud.nativsql.db.mysql.MySQLDialect;
import ovh.heraud.nativsql.db.oracle.OracleDialect;

/**
 * Minimal test configuration with only dialect beans (no datasources or transaction managers).
 * PostgreSQL config is in nativsql-postgres module.
 */
@Configuration
public class TestMinimalConfig {

    @Autowired
    private AnnotationManager annotationManager;

    @Bean(name = "mySQLDialect")
    public MySQLDialect mySQLDialect() {
        MySQLDialect dialect = new MySQLDialect();
        annotationManager.setJsonInfo(ovh.heraud.nativsql.domain.mysql.Address.class);
        annotationManager.setJsonInfo(ovh.heraud.nativsql.domain.mysql.Preferences.class);
        return dialect;
    }

    @Bean(name = "mariaDBDialect")
    public MariaDBDialect mariaDBDialect() {
        MariaDBDialect dialect = new MariaDBDialect();
        annotationManager.setJsonInfo(ovh.heraud.nativsql.domain.mariadb.Address.class);
        annotationManager.setJsonInfo(ovh.heraud.nativsql.domain.mariadb.Preferences.class);
        return dialect;
    }

    @Bean(name = "oracleDialect")
    public OracleDialect oracleDialect() {
        OracleDialect dialect = new OracleDialect();
        annotationManager.setJsonInfo(ovh.heraud.nativsql.domain.oracle.Address.class);
        annotationManager.setJsonInfo(ovh.heraud.nativsql.domain.oracle.Preferences.class);
        return dialect;
    }
}
