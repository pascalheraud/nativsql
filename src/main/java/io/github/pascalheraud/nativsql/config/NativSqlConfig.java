package io.github.pascalheraud.nativsql.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for NativSQL components.
 *
 * <p>
 * This configuration provides the ObjectMapper for JSON serialization.
 * Application components (INativSQLConfiguration) register their types
 * directly with their DatabaseDialect instances.
 * </p>
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

}