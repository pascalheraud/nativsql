package io.github.pascalheraud.nativsql.db.mariadb;

import io.github.pascalheraud.nativsql.db.DefaultDialect;
import io.github.pascalheraud.nativsql.mapper.ITypeMapper;
import org.springframework.stereotype.Component;

/**
 * MariaDB-specific database dialect.
 * Handles MariaDB-specific type mappings including JSON support.
 */
@Component
public class MariaDBDialect extends DefaultDialect {

    public MariaDBDialect() {
        super();
    }

    @Override
    public <T> ITypeMapper<T> getJsonMapper(Class<T> jsonClass) {
        return (ITypeMapper<T>) new MariaDBJSONTypeMapper<>(jsonClass);
    }
}
