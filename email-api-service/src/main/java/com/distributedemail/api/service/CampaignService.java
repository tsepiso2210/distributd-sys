package com.distributedemail.api.service;

import com.distributedemail.api.kafka.EmailKafkaProducer;
import com.distributedemail.api.repository.EmailCampaignRepository;
import com.distributedemail.api.repository.EmailTaskRepository;
import com.distributedemail.api.repository.EmailTemplateRepository;
import com.distributedemail.common.dto.BulkEmailRequestDto;
import com.distributedemail.common.dto.CampaignResponseDto;
import com.distributedemail.common.entity.EmailCampaign;
import com.distributedemail.common.entity.EmailTask;
import com.distributedemail.common.entity.EmailTemplate;
import com.distributedemail.common.enums.EmailPriority;
import com.distributedemail.common.enums.EmailStatus;
import com.distributedemail.common.enums.ProviderName;
import com.distributedemail.common.kafka.EmailTaskMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CampaignService - core business logic for creating and managing email campaigns.
 *
 * Assignment requirements covered:
 * - "API stores the request in PostgreSQL"
 * - "API publishes one email task per recipient to Kafka"
 * - "Email templates with dynamic placeholders"
 * - "Priority messaging so high-priority emails are processed first"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final EmailCampaignRepository campaignRepository;
    private final EmailTaskRepository taskRepository;
    private final EmailTemplateRepository templateRepository;
    private final EmailKafkaProducer kafkaProducer;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new campaign and publishes one Kafka message per recipient.
     *
     * Distributed Systems Design Note:
     *   We use a database-first approach: persist the campaign and all tasks first,
     *   then publish to Kafka. This ensures that even if Kafka is temporarily unavailable,
     *   the tasks are not lost — a recovery job could re-publish PENDING tasks.
     *   This is the "outbox pattern" for distributed systems reliability.
     *
     * @param request The bulk email request from the API client
     * @return Campaign creation summary
     */
    @Transactional
    public CampaignResponseDto createCampaign(BulkEmailRequestDto request) {
        log.info("Creating campaign '{}' with {} recipients, priority: {}",
            request.getCampaignName(), request.getRecipients().size(), request.getPriority());

        // Step 1: Persist the campaign
        EmailCampaign campaign = EmailCampaign.builder()
            .name(request.getCampaignName())
            .subject(request.getSubject())
            .templateId(request.getTemplateId())
            .rawBody(request.getRawBody())
            .senderEmail(request.getSenderEmail())
            .senderName(request.getSenderName())
            .totalRecipients(request.getRecipients().size())
            .build();

        campaign = campaignRepository.save(campaign);
        log.debug("Saved campaign with ID: {}", campaign.getId());

        // Step 2: Resolve the email body (from template or raw body)
        String templateBodyForRendering = resolveTemplateBody(request);
        String subjectTemplate = resolveSubjectTemplate(request);

        // Step 3: Create one EmailTask per recipient and publish to Kafka
        List<EmailTask> tasks = new ArrayList<>();
        for (BulkEmailRequestDto.RecipientDto recipient : request.getRecipients()) {
            // Render the template for this specific recipient
            // Assignment requirement: "Support template variables like {{name}}, {{studentNumber}}, {{course}}"
            String renderedBody = renderTemplate(templateBodyForRendering, recipient);
            String renderedSubject = renderTemplate(subjectTemplate, recipient);

            String variablesJson = serializeVariables(recipient.getTemplateVariables());

            EmailTask task = EmailTask.builder()
                .campaign(campaign)
                .recipientEmail(recipient.getEmail())
                .recipientName(recipient.getName())
                .templateVariables(variablesJson)
                .priority(request.getPriority())
                .status(EmailStatus.PENDING)
                .providerUsed(ProviderName.NONE)
                .retryCount(0)
                .build();

            task = taskRepository.save(task);
            tasks.add(task);

            // Step 4: Publish to Kafka topic based on priority
            // HIGH priority -> email.high-priority topic
            // NORMAL priority -> email.normal-priority topic
            EmailTaskMessage message = EmailTaskMessage.builder()
                .taskId(task.getId())
                .campaignId(campaign.getId())
                .recipientEmail(recipient.getEmail())
                .recipientName(recipient.getName())
                .subject(renderedSubject)
                .body(renderedBody)
                .templateVariables(recipient.getTemplateVariables())
                .priority(request.getPriority())
                .lastAttemptedProvider(ProviderName.NONE)
                .retryCount(0)
                .senderEmail(request.getSenderEmail())
                .senderName(request.getSenderName())
                .createdAt(LocalDateTime.now())
                .build();

            kafkaProducer.publishEmailTask(message);

            // Update task status to QUEUED now that it's on Kafka
            task.setStatus(EmailStatus.QUEUED);
            taskRepository.save(task);
        }

        log.info("Campaign {} created: {} tasks published to Kafka", campaign.getId(), tasks.size());

        return CampaignResponseDto.builder()
            .id(campaign.getId())
            .name(campaign.getName())
            .subject(campaign.getSubject())
            .totalRecipients(tasks.size())
            .createdAt(campaign.getCreatedAt())
            .status("QUEUED")
            .message(String.format("Campaign created. %d email tasks published to Kafka.", tasks.size()))
            .build();
    }

    /**
     * Template renderer - replaces {{placeholder}} tokens with actual values.
     *
     * Assignment requirement: "Support template variables like {{name}}, {{studentNumber}}, {{course}}"
     *
     * @param template Text containing {{placeholder}} tokens
     * @param recipient Recipient data used to resolve placeholders
     * @return Rendered string with all placeholders replaced
     */
    private String renderTemplate(String template, BulkEmailRequestDto.RecipientDto recipient) {
        if (template == null) return "";

        String rendered = template;

        // Replace common built-in variables
        if (recipient.getName() != null) {
            rendered = rendered.replace("{{name}}", recipient.getName());
        }
        rendered = rendered.replace("{{email}}", recipient.getEmail());

        // Replace custom per-recipient variables
        if (recipient.getTemplateVariables() != null) {
            for (var entry : recipient.getTemplateVariables().entrySet()) {
                rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }

        return rendered;
    }

    private String resolveTemplateBody(BulkEmailRequestDto request) {
        if (request.getTemplateId() != null) {
            Optional<EmailTemplate> template = templateRepository.findById(request.getTemplateId());
            if (template.isPresent()) {
                return template.get().getBodyTemplate();
            }
            log.warn("Template ID {} not found, falling back to raw body", request.getTemplateId());
        }
        return request.getRawBody() != null ? request.getRawBody() : "";
    }

    private String resolveSubjectTemplate(BulkEmailRequestDto request) {
        if (request.getTemplateId() != null) {
            Optional<EmailTemplate> template = templateRepository.findById(request.getTemplateId());
            if (template.isPresent()) {
                return template.get().getSubjectTemplate();
            }
        }
        return request.getSubject();
    }

    private String serializeVariables(java.util.Map<String, String> variables) {
        if (variables == null) return null;
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize template variables: {}", e.getMessage());
            return null;
        }
    }

    public List<EmailCampaign> getAllCampaigns() {
        return campaignRepository.findAll();
    }

    public Optional<EmailCampaign> getCampaignById(Long id) {
        return campaignRepository.findById(id);
    }

    public List<EmailTask> getTasksByCampaign(Long campaignId) {
        return taskRepository.findByCampaign_Id(campaignId);
    }

    public List<EmailTask> getFailedTasks() {
        return taskRepository.findFailedTasks();
    }
}
