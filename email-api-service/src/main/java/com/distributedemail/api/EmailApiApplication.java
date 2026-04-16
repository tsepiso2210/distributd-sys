package com.distributedemail.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * EmailApiApplication - Entry point for the email-api-service.
 *
 * This service:
 *   1. Exposes REST endpoints for creating and monitoring campaigns
 *   2. Validates incoming requests
 *   3. Persists campaigns and tasks to PostgreSQL
 *   4. Publishes Kafka messages (one per recipient) for worker consumption
 *
 * Runs on port 8081 (see application.yml)
 */
@SpringBootApplication
// Scan entities from the common module (different package hierarchy)
@EntityScan(basePackages = {"com.distributedemail.common.entity"})
// Scan repositories from the API service
@EnableJpaRepositories(basePackages = {"com.distributedemail.api.repository"})
public class EmailApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailApiApplication.class, args);
    }
}
