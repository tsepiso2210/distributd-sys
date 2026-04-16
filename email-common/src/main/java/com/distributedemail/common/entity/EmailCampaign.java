package com.distributedemail.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EmailCampaign - represents a bulk email campaign.
 *
 * A campaign groups many individual email tasks together.
 * When a client submits a bulk email request, one campaign is created,
 * then one EmailTask is created per recipient.
 *
 * Assignment requirement: "Distributed system for sending bulk email requests"
 *
 * Database table: email_campaigns
 */
@Entity
@Table(name = "email_campaigns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable name for this campaign (e.g., "Welcome Batch - April 2026") */
    @Column(nullable = false)
    private String name;

    /** Subject line - may contain template variables like {{course}} */
    @Column(nullable = false)
    private String subject;

    /** If null, rawBody is used; if set, the template body is rendered per recipient */
    @Column(name = "template_id")
    private Long templateId;

    /**
     * Raw email body used when no template is selected.
     * May contain template placeholders ({{name}}, {{studentNumber}}, {{course}})
     * that are resolved per recipient using recipient metadata.
     */
    @Column(name = "raw_body", columnDefinition = "TEXT")
    private String rawBody;

    /** Who this campaign email appears to be from */
    @Column(name = "sender_email")
    private String senderEmail;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "total_recipients")
    private Integer totalRecipients;

    /** Populated by the system as worker processes tasks */
    @Column(name = "sent_count")
    @Builder.Default
    private Integer sentCount = 0;

    @Column(name = "failed_count")
    @Builder.Default
    private Integer failedCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** One campaign has many email tasks (one per recipient) */
    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EmailTask> tasks;
}
