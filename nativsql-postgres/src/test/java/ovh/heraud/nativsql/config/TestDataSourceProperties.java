package ovh.heraud.nativsql.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Properties holder for PostgreSQL test datasource.
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
}
