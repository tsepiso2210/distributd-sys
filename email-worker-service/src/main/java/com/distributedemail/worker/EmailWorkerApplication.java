package com.distributedemail.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EmailWorkerApplication - entry point for the email-worker-service.
 *
 * This service:
 *   1. Consumes messages from Kafka topics (high-priority and normal-priority)
 *   2. Sends emails through the provider layer (Mailgun -> SendGrid failover)
 *   3. Implements exponential backoff retries
 *   4. Enforces rate limits per provider
 *   5. Updates task status in PostgreSQL after each attempt
 *   6. Publishes failed tasks to retry topic or dead-letter topic
 *
 * Runs on port 8082 (see application.yml)
 */
@SpringBootApplication
@EntityScan(basePackages = {"com.distributedemail.common.entity"})
@EnableJpaRepositories(basePackages = {"com.distributedemail.worker"})
@EnableScheduling
public class EmailWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailWorkerApplication.class, args);
    }
}
