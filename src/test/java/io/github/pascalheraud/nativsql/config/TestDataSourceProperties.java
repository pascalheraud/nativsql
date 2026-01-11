package io.github.pascalheraud.nativsql.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Properties holder for test datasources.
 * Reads datasource properties from dynamic configuration.
 */
@Component
public class TestDataSourceProperties {

    @Value("${spring.datasource.pg.url}")
    private String pgUrl;

    @Value("${spring.datasource.pg.username}")
    private String pgUsername;

    @Value("${spring.datasource.pg.password}")
    private String pgPassword;

    // @Value("${spring.datasource-secondary.url}")
    // private String secondaryUrl;

    // @Value("${spring.datasource-secondary.username}")
    // private String secondaryUsername;

    // @Value("${spring.datasource-secondary.password}")
    // private String secondaryPassword;

    public String getPgUrl() {
        return pgUrl;
    }

    public String getPgUsername() {
        return pgUsername;
    }

    public String getPgPassword() {
        return pgPassword;
    }

    // public String getSecondaryUrl() {
    //     return secondaryUrl;
    // }

    // public String getSecondaryUsername() {
    //     return secondaryUsername;
    // }

    // public String getSecondaryPassword() {
    //     return secondaryPassword;
    // }
}
