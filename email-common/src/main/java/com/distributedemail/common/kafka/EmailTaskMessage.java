package com.distributedemail.common.kafka;

import com.distributedemail.common.enums.EmailPriority;
import com.distributedemail.common.enums.ProviderName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * EmailTaskMessage - the Kafka message payload published to email topics.
 *
 * Assignment requirement: "API publishes one email task per recipient to Kafka"
 *
 * This is what gets serialized as JSON and sent to:
 *   - email.high-priority   (when priority = HIGH)
 *   - email.normal-priority (when priority = NORMAL)
 *   - email.retry           (when a send attempt fails but retries remain)
 *   - email.dead-letter     (when max retries are exceeded)
 *
 * The worker deserializes this from Kafka and uses it to:
 *   1. Look up the full task record in PostgreSQL
 *   2. Render the email body using templateVariables
 *   3. Send via the selected provider
 *   4. Update the task status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTaskMessage {

    /** Primary key of the EmailTask in PostgreSQL */
    private Long taskId;

    /** Primary key of the parent EmailCampaign */
    private Long campaignId;

    /** Email address of the recipient */
    private String recipientEmail;

    /** Full name of the recipient (used in template {{name}}) */
    private String recipientName;

    /** Resolved subject line */
    private String subject;

    /** Resolved email body (templates already applied, or raw body if no template) */
    private String body;

    /**
     * Dynamic template variables map.
     * Example: {"name": "Alice", "studentNumber": "ST001", "course": "CS401"}
     * These are merged with recipient data to render template placeholders.
     */
    private Map<String, String> templateVariables;

    /** Priority of this email - drives routing to the appropriate Kafka topic */
    private EmailPriority priority;

    /**
     * Which provider was last attempted.
     * Set to NONE for first attempt.
     * If MAILGUN fails, worker retries with SENDGRID (failover).
     */
    @Builder.Default
    private ProviderName lastAttemptedProvider = ProviderName.NONE;

    /** Number of retry attempts so far (used to compute backoff delay) */
    @Builder.Default
    private int retryCount = 0;

    /** Sender email address */
    private String senderEmail;

    /** Sender display name */
    private String senderName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
