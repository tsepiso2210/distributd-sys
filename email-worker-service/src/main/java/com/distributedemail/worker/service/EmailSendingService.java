package com.distributedemail.worker.service;

import com.distributedemail.common.entity.EmailStatusLog;
import com.distributedemail.common.entity.EmailTask;
import com.distributedemail.common.enums.EmailStatus;
import com.distributedemail.common.enums.ProviderName;
import com.distributedemail.common.kafka.EmailTaskMessage;
import com.distributedemail.common.provider.EmailProvider;
import com.distributedemail.worker.repository.EmailStatusLogRepository;
import com.distributedemail.worker.repository.EmailTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * EmailSendingService - orchestrates the full email delivery pipeline.
 *
 * Assignment requirements covered:
 * - "Worker consumes messages and sends emails"
 * - "If provider 1 fails, automatically try provider 2"
 * - "If sending still fails, send the task to retry topic"
 * - "Use exponential backoff for retries"
 * - "After max retries, move task to dead-letter topic"
 * - "Every send attempt must be logged in PostgreSQL"
 * - "Apply provider-based rate limiting"
 *
 * This service is called by the Kafka consumers (EmailConsumer) for
 * every message received from any email topic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSendingService {

    private final ProviderSelectionService providerSelectionService;
    private final RateLimiterService rateLimiterService;
    private final RetryService retryService;
    private final EmailTaskRepository taskRepository;
    private final EmailStatusLogRepository statusLogRepository;

    /**
     * Process a single email task message from Kafka.
     *
     * Workflow:
     * 1. Look up the task in PostgreSQL
     * 2. Idempotency check — skip if already delivered
     * 3. Apply rate limiting for the target provider
     * 4. Attempt to send via provider (with automatic failover)
     * 5. Log the result in PostgreSQL  (every attempt logged)
     * 6. Update task status
     * 7. Return the outcome so the consumer can decide Kafka acknowledgment
     *
     * @param message The email task from Kafka
     * @return The send result
     */
    @Transactional
    public SendOutcome processEmailTask(EmailTaskMessage message) {
        log.info("Processing email task {} for recipient {}",
            message.getTaskId(), message.getRecipientEmail());

        // Retrieve the task from database
        Optional<EmailTask> taskOpt = taskRepository.findById(message.getTaskId());
        if (taskOpt.isEmpty()) {
            log.error("Task {} not found in database - skipping", message.getTaskId());
            return SendOutcome.SKIP;
        }

        EmailTask task = taskOpt.get();

        // Idempotency check: skip if already delivered
        // This prevents double-send when Kafka re-delivers a message
        if (task.getStatus() == EmailStatus.DELIVERED || task.getStatus() == EmailStatus.SENT) {
            log.warn("Task {} is already {} — skipping to prevent duplicate delivery",
                task.getId(), task.getStatus());
            return SendOutcome.SKIP;
        }

        // Mark as actively sending
        updateTaskStatus(task, EmailStatus.SENDING, message.getLastAttemptedProvider(), null);

        // Determine which provider to use based on what was last attempted
        ProviderName providerToUse = (message.getLastAttemptedProvider() == ProviderName.NONE)
            ? ProviderName.MAILGUN
            : providerSelectionService.getNextProvider(message.getLastAttemptedProvider());

        // Apply rate limiting — this call blocks until a token is available
        // Assignment requirement: "Apply provider-based rate limiting"
        rateLimiterService.acquireToken(providerToUse);

        // Attempt to send the email (with automatic failover inside)
        EmailProvider.SendResult result = providerSelectionService.sendWithFailover(
            message.getLastAttemptedProvider(),
            message.getSenderEmail(),
            message.getSenderName(),
            message.getRecipientEmail(),
            message.getRecipientName(),
            message.getSubject(),
            message.getBody(),
            stripHtml(message.getBody())
        );

        // Log the attempt in PostgreSQL — every attempt gets a log entry
        // Assignment requirement: "Every send attempt must be logged in PostgreSQL"
        recordAttemptLog(task, result);

        if (result.success()) {
            // Success: update task to SENT
            updateTaskStatus(task, EmailStatus.SENT, result.provider(), null);
            log.info("Email task {} sent successfully via {}", task.getId(), result.provider());
            return SendOutcome.SUCCESS;

        } else {
            // Failure: decide whether to retry or dead-letter
            int nextRetryCount = message.getRetryCount() + 1;

            if (retryService.shouldRetry(message.getRetryCount())) {
                // Still have retry attempts remaining
                // Assignment requirement: "If sending still fails, send the task to retry topic"
                task.setRetryCount(nextRetryCount);
                task.setLastError(result.errorMessage());
                updateTaskStatus(task, EmailStatus.RETRYING, result.provider(), result.errorMessage());
                taskRepository.save(task);

                log.warn("Email task {} failed (attempt {} of {}), scheduling retry",
                    task.getId(), nextRetryCount, retryService.getMaxAttempts());
                return SendOutcome.RETRY;

            } else {
                // All retries exhausted — move to dead-letter
                // Assignment requirement: "After max retries, move task to dead-letter topic"
                task.setLastError(result.errorMessage());
                updateTaskStatus(task, EmailStatus.DEAD_LETTERED, result.provider(), result.errorMessage());
                taskRepository.save(task);

                log.error("Email task {} exhausted all {} retries — moving to dead-letter",
                    task.getId(), retryService.getMaxAttempts());
                return SendOutcome.DEAD_LETTER;
            }
        }
    }

    /**
     * Update the task status and immediately persist to PostgreSQL.
     * Assignment requirement: "Maintain email status history"
     */
    private void updateTaskStatus(EmailTask task, EmailStatus newStatus,
                                   ProviderName provider, String errorMessage) {
        task.setStatus(newStatus);
        if (provider != null && provider != ProviderName.NONE) {
            task.setProviderUsed(provider);
        }
        if (errorMessage != null) {
            task.setLastError(errorMessage);
        }
        taskRepository.save(task);
    }

    /**
     * Write an immutable log entry for every send attempt.
     * Uses a distinct variable name (statusLogEntry) to avoid shadowing
     * the Lombok-generated {@code log} field from @Slf4j.
     *
     * Assignment requirement: "Every send attempt must be logged in PostgreSQL"
     */
    private void recordAttemptLog(EmailTask task, EmailProvider.SendResult result) {
        EmailStatusLog statusLogEntry = EmailStatusLog.builder()
            .emailTask(task)
            .status(result.success() ? EmailStatus.SENT : EmailStatus.FAILED)
            .provider(result.provider())
            .message(result.success()
                ? "Sent — messageId: " + result.messageId()
                : result.errorMessage())
            .responseCode(result.statusCode() > 0 ? result.statusCode() : null)
            .timestamp(LocalDateTime.now())
            .build();

        statusLogRepository.save(statusLogEntry);
        log.debug("Logged attempt for task {}: status={}, provider={}",
            task.getId(), statusLogEntry.getStatus(), statusLogEntry.getProvider());
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * The outcome of processing a single email task.
     * Used by the Kafka consumer to decide what to do after processing.
     */
    public enum SendOutcome {
        /** Email sent successfully — commit Kafka offset */
        SUCCESS,
        /** Email failed but retries remain — re-publish to retry topic */
        RETRY,
        /** Email failed permanently — move to dead-letter topic */
        DEAD_LETTER,
        /** Message was skipped (already processed or task not found) */
        SKIP
    }
}
