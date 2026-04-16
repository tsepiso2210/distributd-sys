package com.distributedemail.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * EmailTemplate - reusable email body/subject with dynamic placeholders.
 *
 * Assignment requirement: "Email templates with dynamic placeholders"
 *
 * Supported placeholders:
 *   {{name}}          - recipient's full name
 *   {{studentNumber}} - student number
 *   {{course}}        - course name
 *   {{email}}         - recipient's email address
 *   (any custom key in the templateVariables JSON map)
 *
 * Example template body:
 *   "Hello {{name}},
 *    Your assignment for {{course}} is due soon.
 *    Student Number: {{studentNumber}}"
 *
 * Database table: email_templates
 */
@Entity
@Table(name = "email_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Descriptive name to identify this template in the GUI */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Subject line template - may contain placeholders.
     * Example: "Assignment Due: {{course}}"
     */
    @Column(name = "subject_template", nullable = false)
    private String subjectTemplate;

    /**
     * Full email body template - may contain placeholders.
     * Supports HTML content.
     */
    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    /** Description to help users pick the right template */
    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
