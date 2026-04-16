package com.distributedemail.common.entity;

import com.distributedemail.common.enums.EmailPriority;
import com.distributedemail.common.enums.EmailStatus;
import com.distributedemail.common.enums.ProviderName;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EmailTask - one email to one recipient.
 *
 * When a campaign is created, the API publishes one EmailTask per recipient
 * to a Kafka topic based on the priority level.
 *
 * Assignment requirements covered:
 * - "Email status tracking: sent, delivered, bounced, failed"
 * - "Retry mechanism for failed email deliveries using exponential backoff"
 * - "Priority messaging so high-priority emails are processed first"
 * - "Automatic failover to another email provider if one fails"
 *
 * Database table: email_tasks
 */
@Entity
@Table(name = "email_tasks",
    indexes = {
        @Index(name = "idx_email_tasks_status", columnList = "status"),
        @Index(name = "idx_email_tasks_campaign", columnList = "campaign_id"),
        @Index(name = "idx_email_tasks_priority", columnList = "priority")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Parent campaign this task belongs to */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private EmailCampaign campaign;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "recipient_name")
    private String recipientName;

    /**
     * Additional dynamic variables for template rendering.
     * Stored as JSON string, e.g.: {"studentNumber":"ST001","course":"CS401"}
     * These map to {{studentNumber}} and {{course}} in templates.
     * Assignment requirement: "Support template variables"
     */
    @Column(name = "template_variables", columnDefinition = "TEXT")
    private String templateVariables;

    /**
     * Priority determines which Kafka topic this task is published to.
     * HIGH   -> email.high-priority topic (workers poll first)
     * NORMAL -> email.normal-priority topic
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EmailPriority priority = EmailPriority.NORMAL;

    /** Current status of this email delivery attempt */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EmailStatus status = EmailStatus.PENDING;

    /**
     * Which provider was last used to attempt delivery.
     * Used by the worker to track failover:
     *   NONE -> try MAILGUN first
     *   MAILGUN failed -> try SENDGRID (failover)
     *   SENDGRID failed -> move to dead-letter
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_used")
    @Builder.Default
    private ProviderName providerUsed = ProviderName.NONE;

    /**
     * Number of retry attempts so far.
     * When retryCount >= maxRetries (configurable), task moves to dead-letter topic.
     * Used by exponential backoff: delay = baseDelay * 2^retryCount
     */
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    /** Human-readable description of the last error for debugging */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Audit trail of every status change for this email task */
    @OneToMany(mappedBy = "emailTask", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EmailStatusLog> statusLogs;
}
