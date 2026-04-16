package com.distributedemail.common.dto;

import com.distributedemail.common.enums.EmailPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * BulkEmailRequestDto - the payload the client sends to the REST API.
 *
 * Assignment requirement: "Client submits a bulk email request with:
 *   - recipients
 *   - subject
 *   - body or template ID
 *   - priority"
 *
 * Sample JSON:
 * {
 *   "campaignName": "Welcome Email Batch",
 *   "subject": "Welcome to {{course}}!",
 *   "templateId": null,
 *   "rawBody": "Hello {{name}}, welcome to the Distributed Systems course!",
 *   "priority": "HIGH",
 *   "senderEmail": "no-reply@university.edu",
 *   "senderName": "University Admin",
 *   "recipients": [
 *     {
 *       "email": "alice@student.edu",
 *       "name": "Alice Smith",
 *       "templateVariables": {
 *         "studentNumber": "ST001",
 *         "course": "CS401"
 *       }
 *     }
 *   ]
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkEmailRequestDto {

    @NotBlank(message = "Campaign name is required")
    private String campaignName;

    @NotBlank(message = "Subject is required")
    private String subject;

    /** If set, the system looks up this template and renders it per recipient */
    private Long templateId;

    /** Used when templateId is null */
    private String rawBody;

    @NotNull(message = "Priority is required (HIGH or NORMAL)")
    private EmailPriority priority;

    @Email(message = "Sender email must be valid")
    @NotBlank(message = "Sender email is required")
    private String senderEmail;

    private String senderName;

    @NotEmpty(message = "At least one recipient is required")
    @Valid
    private List<RecipientDto> recipients;

    /**
     * RecipientDto - one entry per email to be sent.
     * Each recipient can have unique template variables.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecipientDto {

        @Email(message = "Recipient email must be valid")
        @NotBlank(message = "Recipient email is required")
        private String email;

        private String name;

        /**
         * Per-recipient template variable overrides.
         * These are merged with campaign-level variables during template rendering.
         * Example: {"studentNumber": "ST001", "course": "CS401"}
         */
        private Map<String, String> templateVariables;
    }
}
