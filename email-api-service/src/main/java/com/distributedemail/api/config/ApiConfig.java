package com.distributedemail.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ApiConfig - general application configuration beans.
 */
@Configuration
public class ApiConfig {

    /**
     * ObjectMapper with Java 8 date/time support.
     * Used for serializing LocalDateTime in Kafka messages and API responses.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
