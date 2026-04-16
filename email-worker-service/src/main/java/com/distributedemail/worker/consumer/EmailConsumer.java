package com.distributedemail.worker.consumer;

import com.distributedemail.common.enums.EmailPriority;
import com.distributedemail.common.kafka.EmailTaskMessage;
import com.distributedemail.common.kafka.KafkaTopics;
import com.distributedemail.worker.service.EmailSendingService;
import com.distributedemail.worker.service.RetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * EmailConsumer - Kafka consumers for all email topics.
 *
 * Assignment requirement: "Worker consumes messages and sends emails"
 *
 * Three listener methods:
 *
 * 1. consumeHighPriority() - listens on email.high-priority
 *    Uses higher concurrency (5 threads) via containerFactory=highPriority
 *    "Priority emails must be processed ahead of normal ones"
 *
 * 2. consumeNormalPriority() - listens on email.normal-priority
 *    Uses lower concurrency (2 threads) via containerFactory=normalPriority
 *
 * 3. consumeRetry() - listens on email.retry
 *    Applies exponential backoff delay before re-processing
 *    "Retry mechanism for failed email deliveries using exponential backoff"
 *
 * Distributed Systems Design Note:
 *   We use manual acknowledgment (AckMode.MANUAL_IMMEDIATE) to avoid
 *   committing the offset until we have successfully processed the message.
 *   This ensures "at-least-once" delivery semantics — if the worker crashes
 *   mid-processing, the message will be re-delivered to another worker.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConsumer {

    private final EmailSendingService emailSendingService;
    private final RetryService retryService;
    private final KafkaTemplate<String, EmailTaskMessage> kafkaTemplate;

    /**
     * Consume HIGH-priority emails.
     * Assignment requirement: "High-priority emails must be processed ahead of normal ones"
     *
     * Uses highPriorityConcurrency (5 threads) so more messages are
     * processed simultaneously compared to the normal-priority consumer.
     */
    @KafkaListener(
        topics = KafkaTopics.HIGH_PRIORITY,
        groupId = "email-worker-group",
        containerFactory = "highPriorityListenerContainerFactory"
    )
    public void consumeHighPriority(
            ConsumerRecord<String, EmailTaskMessage> record,
            Acknowledgment acknowledgment) {

        log.info("HIGH PRIORITY: processing task {} (partition {}, offset {})",
            record.value().getTaskId(), record.partition(), record.offset());

        processMessage(record.value(), acknowledgment);
    }

    /**
     * Consume NORMAL-priority emails.
     * Uses normalPriorityConcurrency (2 threads).
     */
    @KafkaListener(
        topics = KafkaTopics.NORMAL_PRIORITY,
        groupId = "email-worker-group",
        containerFactory = "normalPriorityListenerContainerFactory"
    )
    public void consumeNormalPriority(
            ConsumerRecord<String, EmailTaskMessage> record,
            Acknowledgment acknowledgment) {

        log.info("NORMAL PRIORITY: processing task {} (partition {}, offset {})",
            record.value().getTaskId(), record.partition(), record.offset());

        processMessage(record.value(), acknowledgment);
    }

    /**
     * Consume RETRY emails.
     *
     * Assignment requirement: "Use exponential backoff for retries"
     *
     * Before processing, we apply the exponential backoff delay based on
     * the retry count. This prevents hammering a failing provider.
     */
    @KafkaListener(
        topics = KafkaTopics.RETRY,
        groupId = "email-worker-retry-group",
        containerFactory = "retryListenerContainerFactory"
    )
    public void consumeRetry(
            ConsumerRecord<String, EmailTaskMessage> record,
            Acknowledgment acknowledgment) {

        EmailTaskMessage message = record.value();
        log.info("RETRY: processing task {} (attempt {})", message.getTaskId(), message.getRetryCount() + 1);

        // Apply exponential backoff delay BEFORE processing
        // This prevents the retry storm / thundering herd problem
        retryService.waitForBackoff(message.getRetryCount());

        processMessage(message, acknowledgment);
    }

    /**
     * Core message processing: send the email and handle the outcome.
     */
    private void processMessage(EmailTaskMessage message, Acknowledgment acknowledgment) {
        try {
            EmailSendingService.SendOutcome outcome = emailSendingService.processEmailTask(message);

            switch (outcome) {
                case SUCCESS, SKIP -> {
                    // Commit the Kafka offset - message fully processed
                    acknowledgment.acknowledge();
                }
                case RETRY -> {
                    // Increment retry count and re-publish to retry topic
                    EmailTaskMessage retryMessage = buildRetryMessage(message);
                    kafkaTemplate.send(KafkaTopics.RETRY,
                        String.valueOf(message.getTaskId()), retryMessage);
                    acknowledgment.acknowledge();
                    log.info("Task {} queued for retry (attempt {} of {})",
                        message.getTaskId(), message.getRetryCount() + 1, retryService.getMaxAttempts());
                }
                case DEAD_LETTER -> {
                    // Move to dead-letter topic for manual review
                    kafkaTemplate.send(KafkaTopics.DEAD_LETTER,
                        String.valueOf(message.getTaskId()), message);
                    acknowledgment.acknowledge();
                    log.error("Task {} moved to dead-letter after {} failed attempts",
                        message.getTaskId(), message.getRetryCount() + 1);
                }
            }

        } catch (Exception e) {
            // Unexpected error: do NOT acknowledge so Kafka will re-deliver
            log.error("Unexpected error processing task {}: {}", message.getTaskId(), e.getMessage(), e);
            // Let the offset stay uncommitted - Kafka will retry delivery
        }
    }

    /**
     * Build a retry message with incremented retry count.
     */
    private EmailTaskMessage buildRetryMessage(EmailTaskMessage original) {
        return EmailTaskMessage.builder()
            .taskId(original.getTaskId())
            .campaignId(original.getCampaignId())
            .recipientEmail(original.getRecipientEmail())
            .recipientName(original.getRecipientName())
            .subject(original.getSubject())
            .body(original.getBody())
            .templateVariables(original.getTemplateVariables())
            .priority(original.getPriority())
            .lastAttemptedProvider(original.getLastAttemptedProvider())
            .retryCount(original.getRetryCount() + 1)
            .senderEmail(original.getSenderEmail())
            .senderName(original.getSenderName())
            .createdAt(original.getCreatedAt())
            .build();
    }
}
