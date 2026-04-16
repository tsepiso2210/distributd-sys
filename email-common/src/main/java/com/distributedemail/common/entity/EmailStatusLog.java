package com.distributedemail.common.entity;

import com.distributedemail.common.enums.EmailStatus;
import com.distributedemail.common.enums.ProviderName;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * EmailStatusLog - immutable audit record for every status change.
 *
 * Assignment requirement: "Every send attempt must be logged in PostgreSQL"
 *                          "Maintain email status history"
 *
 * Every time the worker attempts to send an email (or the status changes),
 * a new log record is inserted. This gives a full audit trail, for example:
 *   PENDING -> QUEUED -> SENDING -> FAILED -> RETRYING -> SENT
 *
 * Database table: email_status_logs
 */
@Entity
@Table(
    name = "email_status_logs",
    indexes = {
        @Index(name = "idx_status_logs_task",      columnList = "email_task_id"),
        @Index(name = "idx_status_logs_timestamp", columnList = "timestamp")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The email task this log entry belongs to */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_task_id", nullable = false)
    private EmailTask emailTask;

    /** The new status at the time this log entry was created */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailStatus status;

    /** Which provider was involved in this status change (may be null for early states) */
    @Enumerated(EnumType.STRING)
    private ProviderName provider;

    /** Human-readable message (success confirmation or error details) */
    @Column(columnDefinition = "TEXT")
    private String message;

    /** HTTP status code returned by the provider API (null if no HTTP call made) */
    @Column(name = "response_code")
    private Integer responseCode;

    /** When this log entry was created — immutable once set */
    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
