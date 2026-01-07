package io.github.pascalheraud.nativsql.config;

import io.github.pascalheraud.nativsql.mapper.INativSQLMapper;

/**
 * Interface that application components should implement to configure NativSQL type mappers.
 *
 * <p>Implementations should be annotated with {@code @Component} and will be automatically
 * discovered by NativSQL's configuration mechanism.</p>
 *
 * <p>Example implementation:</p>
 * <pre>
 * {@code @Component}
 * public class MyAppNativSQLConfiguration implements INativSQLConfiguration {
 *     {@code @Override}
 *     public void configure(INativSQLMapper mapper) {
 *         // Register composite types with their PostgreSQL type names
 *         mapper.registerCompositeType(Address.class, "address_type");
 *
 *         // Register JSON types
 *         mapper.registerJsonType(Preferences.class);
 *
 *         // Register enum types with their PostgreSQL type names
 *         mapper.registerEnumType(UserStatus.class, "user_status");
 *
 *         // Register custom mappers
 *         mapper.registerCompositeMapper(Email.class, String.class, Email::new);
 *     }
 * }
 * </pre>
 */
public interface INativSQLConfiguration {

    /**
     * Configure NativSQL type mappings.
     *
     * @param mapper the mapper interface to configure custom type mappings
     */
    void configure(INativSQLMapper mapper);
}
