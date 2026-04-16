package com.distributedemail.api.controller;

import com.distributedemail.api.kafka.EmailKafkaProducer;
import com.distributedemail.api.repository.EmailTaskRepository;
import com.distributedemail.common.entity.EmailTask;
import com.distributedemail.common.enums.EmailStatus;
import com.distributedemail.common.enums.ProviderName;
import com.distributedemail.common.kafka.EmailTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * RetryController - allows manual retry of failed/dead-lettered email tasks.
 *
 * Assignment requirement: "Failed Emails / Retry View" GUI view
 *
 * The JavaFX FailedEmailsController calls this endpoint when the user
 * clicks "Retry Selected" for a failed task.
 *
 * POST /api/campaigns/retry-task/{taskId}
 */
@Slf4j
@RestController
@RequestMapping("/api/campaigns")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class RetryController {

    private final EmailTaskRepository taskRepository;
    private final EmailKafkaProducer kafkaProducer;

    /**
     * Manually retry a failed or dead-lettered email task.
     * Resets retry count and re-publishes to the appropriate Kafka topic.
     */
    @PostMapping("/retry-task/{taskId}")
    @Transactional
    public ResponseEntity<Map<String, String>> retryTask(@PathVariable Long taskId) {
        EmailTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        if (task.getStatus() != EmailStatus.FAILED && task.getStatus() != EmailStatus.DEAD_LETTERED) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Task is not in FAILED or DEAD_LETTERED state: " + task.getStatus()));
        }

        // Reset retry count for manual retry
        task.setRetryCount(0);
        task.setStatus(EmailStatus.QUEUED);
        task.setProviderUsed(ProviderName.NONE);
        taskRepository.save(task);

        // Build and re-publish the Kafka message
        EmailTask savedTask = task;
        EmailTaskMessage message = EmailTaskMessage.builder()
            .taskId(savedTask.getId())
            .campaignId(savedTask.getCampaign().getId())
            .recipientEmail(savedTask.getRecipientEmail())
            .recipientName(savedTask.getRecipientName())
            .subject(savedTask.getCampaign().getSubject())
            .body(savedTask.getCampaign().getRawBody())
            .priority(savedTask.getPriority())
            .lastAttemptedProvider(ProviderName.NONE)
            .retryCount(0)
            .senderEmail(savedTask.getCampaign().getSenderEmail())
            .senderName(savedTask.getCampaign().getSenderName())
            .createdAt(LocalDateTime.now())
            .build();

        kafkaProducer.publishEmailTask(message);

        log.info("Manual retry triggered for task {}", taskId);
        return ResponseEntity.ok(Map.of(
            "message", "Task " + taskId + " queued for retry",
            "taskId", String.valueOf(taskId)
        ));
    }
}
