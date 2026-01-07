package io.github.pascalheraud.nativsql.config;

import io.github.pascalheraud.nativsql.domain.Address;
import io.github.pascalheraud.nativsql.domain.Preferences;
import io.github.pascalheraud.nativsql.domain.UserStatus;
import io.github.pascalheraud.nativsql.mapper.INativSQLMapper;
import org.springframework.stereotype.Component;

/**
 * Test configuration component that registers example domain types for testing.
 *
 * <p>This implementation of {@link INativSQLConfiguration} demonstrates how applications
 * should configure NativSQL by implementing the interface and providing type mappings.</p>
 */
@Component
public class TestNativSqlConfig implements INativSQLConfiguration {

    /**
     * Configures NativSQL with test domain types.
     *
     * @param mapper the INativSQLMapper to configure
     */
    @Override
    public void configure(INativSQLMapper mapper) {
        // Register composite types with their PostgreSQL type names
        mapper.registerCompositeType(Address.class, "address_type");

        // Register JSON types
        mapper.registerJsonType(Preferences.class);

        // Register enum types with their PostgreSQL type names
        mapper.registerEnumType(UserStatus.class, "user_status");
        // ContactType is registered via @EnumMapping annotation
    }
}
