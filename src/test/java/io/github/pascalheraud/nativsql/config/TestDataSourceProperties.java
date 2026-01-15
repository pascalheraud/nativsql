package io.github.pascalheraud.nativsql.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Properties holder for test datasources.
 * Reads datasource properties from dynamic configuration.
 */
@Component
@Getter
public class TestDataSourceProperties {

    @Value("${spring.datasource.pg.url:}")
    private String pgUrl;

    @Value("${spring.datasource.pg.username:}")
    private String pgUsername;

    @Value("${spring.datasource.pg.password:}")
    private String pgPassword;


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
}
