package com.distributedemail.api.config;

import com.distributedemail.common.kafka.EmailTaskMessage;
import com.distributedemail.common.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * KafkaProducerConfig - configures the Kafka producer and creates all required topics.
 *
 * Assignment requirement: "Separate Kafka topics must exist for:
 *   - high priority, normal priority, retry, status, dead-letter"
 *
 * Topics are created automatically on startup if they don't exist.
 * In production, pre-create topics with specific partition/replica configs.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.partitions:3}")
    private int partitions;

    @Value("${app.kafka.replicas:1}")
    private short replicas;

    @Bean
    public ProducerFactory<String, EmailTaskMessage> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Wait for all replicas to acknowledge (highest durability)
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, EmailTaskMessage> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // Auto-create all required topics on startup
    // Assignment requirement: all 5 topic types must exist

    @Bean
    public NewTopic highPriorityTopic() {
        return TopicBuilder.name(KafkaTopics.HIGH_PRIORITY)
            .partitions(partitions)
            .replicas(replicas)
            .build();
    }

    @Bean
    public NewTopic normalPriorityTopic() {
        return TopicBuilder.name(KafkaTopics.NORMAL_PRIORITY)
            .partitions(partitions)
            .replicas(replicas)
            .build();
    }

    @Bean
    public NewTopic retryTopic() {
        return TopicBuilder.name(KafkaTopics.RETRY)
            .partitions(partitions)
            .replicas(replicas)
            .build();
    }

    @Bean
    public NewTopic statusTopic() {
        return TopicBuilder.name(KafkaTopics.STATUS)
            .partitions(partitions)
            .replicas(replicas)
            .build();
    }

    @Bean
    public NewTopic deadLetterTopic() {
        // Dead-letter topic: single partition is fine, these are for manual review
        return TopicBuilder.name(KafkaTopics.DEAD_LETTER)
            .partitions(1)
            .replicas(replicas)
            .build();
    }
}
