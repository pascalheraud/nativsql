package io.github.pascalheraud.nativsql.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.pascalheraud.nativsql.mapper.TypeMapperFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for NativSQL components.
 *
 * <p>This configuration automatically discovers all {@link INativSQLConfiguration} components
 * in the application context and uses them to configure the {@link TypeMapperFactory}.</p>
 */
@Configuration
public class NativSqlConfig {

    /**
     * ObjectMapper for JSON serialization/deserialization.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Configures the TypeMapperFactory by delegating to all registered
     * {@link INativSQLConfiguration} components and scanning for @EnumMapping annotations.
     *
     * <p>The application should provide one or more {@code @Component} implementations
     * of {@link INativSQLConfiguration} to register custom type mappers.</p>
     *
     * <p>Enum types annotated with {@code @EnumMapping} will be automatically discovered
     * and registered. The scan packages can be configured with the property
     * {@code nativsql.enum-mapping.scan-packages} (comma-separated).</p>
     *
     * @param objectMapper the Jackson ObjectMapper for JSON serialization
     * @param configurations list of all INativSQLConfiguration components found in the context
     * @param scanPackages comma-separated list of packages to scan for @EnumMapping annotations
     * @return the configured TypeMapperFactory
     */
    @Bean
    public TypeMapperFactory typeMapperFactory(
            ObjectMapper objectMapper,
            @Autowired(required = false) List<INativSQLConfiguration> configurations,
            @Value("${nativsql.enum-mapping.scan-packages:}") String scanPackages) {
        TypeMapperFactory factory = new TypeMapperFactory(objectMapper);

        // Scan for @EnumMapping annotated enums
        if (scanPackages != null && !scanPackages.isEmpty()) {
            String[] packages = scanPackages.split(",");
            for (int i = 0; i < packages.length; i++) {
                packages[i] = packages[i].trim();
            }
            EnumMappingScanner.scanAndRegister(factory, packages);
        }

        // Apply all configurations found in the application context
        if (configurations != null) {
            configurations.forEach(config -> config.configure(factory));
        }

        return factory;
    }
}