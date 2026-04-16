package com.distributedemail.worker.config;

import com.distributedemail.common.kafka.EmailTaskMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * KafkaConsumerConfig - configures separate listener container factories
 * for different priority levels.
 *
 * Assignment requirement: "Priority emails must be processed ahead of normal ones"
 *
 * Distributed Systems Design Note:
 *   By assigning more concurrent threads to the high-priority topic consumer,
 *   we ensure that high-priority messages are processed faster.
 *   The concurrency parameter controls how many consumer threads are created
 *   within this single JVM instance. Each thread handles one partition.
 *
 *   highPriority:   5 concurrent consumer threads
 *   normalPriority: 2 concurrent consumer threads
 *   retry:          1 concurrent consumer thread (retry is sequential/slow)
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.high-priority-concurrency:5}")
    private int highPriorityConcurrency;

    @Value("${app.kafka.normal-priority-concurrency:2}")
    private int normalPriorityConcurrency;

    @Value("${app.kafka.retry-concurrency:1}")
    private int retryConcurrency;

    private ConsumerFactory<String, EmailTaskMessage> createConsumerFactory(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Manual acknowledgment - don't commit offset until explicitly acknowledged
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.distributedemail.common.kafka");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, EmailTaskMessage.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props,
            new StringDeserializer(),
            new JsonDeserializer<>(EmailTaskMessage.class, false));
    }

    private ConcurrentKafkaListenerContainerFactory<String, EmailTaskMessage>
            createContainerFactory(String groupId, int concurrency) {

        ConcurrentKafkaListenerContainerFactory<String, EmailTaskMessage> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(createConsumerFactory(groupId));
        factory.setConcurrency(concurrency);
        // Use manual acknowledgment for at-least-once delivery guarantee
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    /**
     * High-priority consumer factory - 5 concurrent threads.
     * More threads = faster processing of high-priority emails.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EmailTaskMessage>
            highPriorityListenerContainerFactory() {
        return createContainerFactory("email-worker-group", highPriorityConcurrency);
    }

    /**
     * Normal-priority consumer factory - 2 concurrent threads.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EmailTaskMessage>
            normalPriorityListenerContainerFactory() {
        return createContainerFactory("email-worker-group", normalPriorityConcurrency);
    }

    /**
     * Retry consumer factory - 1 thread (sequential retries with backoff).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EmailTaskMessage>
            retryListenerContainerFactory() {
        return createContainerFactory("email-worker-retry-group", retryConcurrency);
    }
}
