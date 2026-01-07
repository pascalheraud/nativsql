package io.github.pascalheraud.nativsql.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.pascalheraud.nativsql.domain.Address;
import io.github.pascalheraud.nativsql.domain.Preferences;
import io.github.pascalheraud.nativsql.domain.UserStatus;
import io.github.pascalheraud.nativsql.mapper.TypeMapperFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for NativSQL components.
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
     * Configures custom type mappers.
     */
    @Bean
    public TypeMapperFactory typeMapperFactory(ObjectMapper objectMapper) {
        TypeMapperFactory factory = new TypeMapperFactory(objectMapper);

        // Register composite types with their PostgreSQL type names
        factory.registerCompositeType(Address.class, "address_type");

        // Register JSON types
        factory.registerJsonType(Preferences.class);

        // Register enum types with their PostgreSQL type names
        factory.registerEnumType(UserStatus.class, "user_status");

        // Additional custom mappers can be registered here
        // Example:
        // factory.registerCompositeMapper(Email.class, String.class, Email::new);

        return factory;
    }
}