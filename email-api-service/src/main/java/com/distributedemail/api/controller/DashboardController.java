package com.distributedemail.api.controller;

import com.distributedemail.api.repository.EmailCampaignRepository;
import com.distributedemail.api.repository.EmailTaskRepository;
import com.distributedemail.common.enums.EmailStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * DashboardController - provides summary statistics for the JavaFX Dashboard view.
 *
 * Assignment requirement: "Dashboard" (GUI view)
 *
 * GET /api/dashboard/stats
 * Returns:
 * {
 *   "totalSent": 500,
 *   "totalFailed": 12,
 *   "totalPending": 33,
 *   "totalDeadLettered": 2,
 *   "recentCampaigns": [...]
 * }
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DashboardController {

    private final EmailTaskRepository taskRepository;
    private final EmailCampaignRepository campaignRepository;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalSent", taskRepository.countByStatus(EmailStatus.SENT));
        stats.put("totalDelivered", taskRepository.countByStatus(EmailStatus.DELIVERED));
        stats.put("totalFailed", taskRepository.countByStatus(EmailStatus.FAILED));
        stats.put("totalPending", taskRepository.countByStatus(EmailStatus.PENDING));
        stats.put("totalQueued", taskRepository.countByStatus(EmailStatus.QUEUED));
        stats.put("totalRetrying", taskRepository.countByStatus(EmailStatus.RETRYING));
        stats.put("totalBounced", taskRepository.countByStatus(EmailStatus.BOUNCED));
        stats.put("totalDeadLettered", taskRepository.countByStatus(EmailStatus.DEAD_LETTERED));
        stats.put("recentCampaigns",
            campaignRepository.findRecentCampaigns(PageRequest.of(0, 10)));

        return ResponseEntity.ok(stats);
    }
}
