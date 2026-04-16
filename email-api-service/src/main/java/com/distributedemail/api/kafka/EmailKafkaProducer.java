package com.distributedemail.api.kafka;

import com.distributedemail.common.kafka.EmailTaskMessage;
import com.distributedemail.common.kafka.KafkaTopics;
import com.distributedemail.common.enums.EmailPriority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * EmailKafkaProducer - publishes email task messages to the appropriate Kafka topic.
 *
 * Assignment requirement: "API publishes one email task per recipient to Kafka"
 *
 * Topic routing:
 *   HIGH priority   -> email.high-priority
 *   NORMAL priority -> email.normal-priority
 *
 * The partition key is the taskId (as string), which ensures that all
 * status updates for the same task land in the same Kafka partition,
 * maintaining message ordering for that task.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailKafkaProducer {

    private final KafkaTemplate<String, EmailTaskMessage> kafkaTemplate;

    /**
     * Publish one email task to the appropriate priority topic.
     *
     * @param message The email task to publish
     */
    public void publishEmailTask(EmailTaskMessage message) {
        // Select topic based on priority (satisfies priority requirement)
        String topic = message.getPriority() == EmailPriority.HIGH
            ? KafkaTopics.HIGH_PRIORITY
            : KafkaTopics.NORMAL_PRIORITY;

        // Use taskId as the partition key to ensure ordering
        String partitionKey = String.valueOf(message.getTaskId());

        CompletableFuture<SendResult<String, EmailTaskMessage>> future =
            kafkaTemplate.send(topic, partitionKey, message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish email task {} to topic {}: {}",
                    message.getTaskId(), topic, ex.getMessage());
            } else {
                log.info("Published email task {} to topic {} partition {} offset {}",
                    message.getTaskId(),
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Publish a task to the retry topic.
     * Called by the worker when a send attempt fails but retries remain.
     *
     * Assignment requirement: "Retry mechanism for failed email deliveries"
     */
    public void publishToRetry(EmailTaskMessage message) {
        String partitionKey = String.valueOf(message.getTaskId());
        kafkaTemplate.send(KafkaTopics.RETRY, partitionKey, message)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish task {} to retry topic: {}",
                        message.getTaskId(), ex.getMessage());
                } else {
                    log.info("Task {} published to retry topic (attempt {})",
                        message.getTaskId(), message.getRetryCount());
                }
            });
    }

    /**
     * Publish a task to the dead-letter topic after all retries are exhausted.
     *
     * Assignment requirement: "After max retries, move task to dead-letter topic"
     */
    public void publishToDeadLetter(EmailTaskMessage message) {
        kafkaTemplate.send(KafkaTopics.DEAD_LETTER, String.valueOf(message.getTaskId()), message)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("CRITICAL: Failed to publish task {} to dead-letter topic: {}",
                        message.getTaskId(), ex.getMessage());
                } else {
                    log.warn("Task {} moved to dead-letter topic after {} retries",
                        message.getTaskId(), message.getRetryCount());
                }
            });
    }
}
