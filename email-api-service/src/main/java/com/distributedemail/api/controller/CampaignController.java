package com.distributedemail.api.controller;

import com.distributedemail.api.service.CampaignService;
import com.distributedemail.common.dto.BulkEmailRequestDto;
import com.distributedemail.common.dto.CampaignResponseDto;
import com.distributedemail.common.entity.EmailCampaign;
import com.distributedemail.common.entity.EmailTask;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CampaignController - REST endpoints for campaign management.
 *
 * Assignment requirement: "REST API to accept bulk email requests"
 *
 * =============================================================
 * Sample REST endpoints and payloads:
 * =============================================================
 *
 * POST /api/campaigns
 * Content-Type: application/json
 * Body:
 * {
 *   "campaignName": "Assignment Due Reminder",
 *   "subject": "Your {{course}} assignment is due soon!",
 *   "templateId": null,
 *   "rawBody": "Hello {{name}}, this is a reminder that your assignment for {{course}} is due in 48 hours. Student Number: {{studentNumber}}",
 *   "priority": "HIGH",
 *   "senderEmail": "admin@university.edu",
 *   "senderName": "CS Department",
 *   "recipients": [
 *     {
 *       "email": "alice@student.edu",
 *       "name": "Alice Smith",
 *       "templateVariables": {
 *         "studentNumber": "ST001",
 *         "course": "CS401 Distributed Systems"
 *       }
 *     },
 *     {
 *       "email": "bob@student.edu",
 *       "name": "Bob Jones",
 *       "templateVariables": {
 *         "studentNumber": "ST002",
 *         "course": "CS401 Distributed Systems"
 *       }
 *     }
 *   ]
 * }
 *
 * GET /api/campaigns
 * GET /api/campaigns/{id}
 * GET /api/campaigns/{id}/tasks
 * GET /api/campaigns/failed-tasks
 */
@Slf4j
@RestController
@RequestMapping("/api/campaigns")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    /**
     * Create a new bulk email campaign.
     * Validates the request, stores it in PostgreSQL, and publishes to Kafka.
     */
    @PostMapping
    public ResponseEntity<CampaignResponseDto> createCampaign(
            @Valid @RequestBody BulkEmailRequestDto request) {

        log.info("Received campaign request: {} recipients, priority: {}",
            request.getRecipients().size(), request.getPriority());

        CampaignResponseDto response = campaignService.createCampaign(request);
        return ResponseEntity.ok(response);
    }

    /** List all campaigns (shown in the dashboard / reports view) */
    @GetMapping
    public ResponseEntity<List<EmailCampaign>> getAllCampaigns() {
        return ResponseEntity.ok(campaignService.getAllCampaigns());
    }

    /** Get campaign details */
    @GetMapping("/{id}")
    public ResponseEntity<EmailCampaign> getCampaignById(@PathVariable Long id) {
        return campaignService.getCampaignById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /** Get all email tasks for a specific campaign */
    @GetMapping("/{id}/tasks")
    public ResponseEntity<List<EmailTask>> getTasksByCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getTasksByCampaign(id));
    }

    /**
     * Get all failed tasks across all campaigns.
     * Used by the "Failed Emails / Retry View" in the JavaFX GUI.
     * Assignment requirement: "Failed Emails / Retry View"
     */
    @GetMapping("/failed-tasks")
    public ResponseEntity<List<EmailTask>> getFailedTasks() {
        return ResponseEntity.ok(campaignService.getFailedTasks());
    }
}
