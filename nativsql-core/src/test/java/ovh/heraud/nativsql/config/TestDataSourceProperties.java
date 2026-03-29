package ovh.heraud.nativsql.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Properties holder for test datasources (MySQL, MariaDB, Oracle).
 * PostgreSQL properties are in nativsql-postgres module.
 * Reads datasource properties from dynamic configuration.
 */
@Component
@Getter
public class TestDataSourceProperties {

    @Value("${spring.datasource.mysql.url:}")
    private String mySQLUrl;

    @Value("${spring.datasource.mysql.username:}")
    private String mySQLUsername;

    @Value("${spring.datasource.mysql.password:}")
    private String mySQLPassword;

    @Value("${spring.datasource.mariadb.url:}")
    private String mariaDBUrl;

    @Value("${spring.datasource.mariadb.username:}")
    private String mariaDBUsername;

    @Value("${spring.datasource.mariadb.password:}")
    private String mariaDBPassword;

    @Value("${spring.datasource.oracle.url:}")
    private String oracleUrl;

    @Value("${spring.datasource.oracle.username:}")
    private String oracleUsername;

    @Value("${spring.datasource.oracle.password:}")
    private String oraclePassword;
}
