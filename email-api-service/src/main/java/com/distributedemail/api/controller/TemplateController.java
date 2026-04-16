package com.distributedemail.api.controller;

import com.distributedemail.api.service.TemplateService;
import com.distributedemail.common.entity.EmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * TemplateController - REST endpoints for template CRUD.
 *
 * Assignment requirement: "Template Management" (GUI view) and
 *   "Email templates with dynamic placeholders"
 *
 * Sample endpoints:
 *   GET    /api/templates
 *   GET    /api/templates/{id}
 *   POST   /api/templates
 *   PUT    /api/templates/{id}
 *   DELETE /api/templates/{id}
 *   POST   /api/templates/{id}/preview
 */
@Slf4j
@RestController
@RequestMapping("/api/templates")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    public ResponseEntity<List<EmailTemplate>> getAllTemplates() {
        return ResponseEntity.ok(templateService.getAllTemplates());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailTemplate> getTemplateById(@PathVariable Long id) {
        return templateService.getTemplateById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<EmailTemplate> createTemplate(@RequestBody EmailTemplate template) {
        return ResponseEntity.ok(templateService.createTemplate(template));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmailTemplate> updateTemplate(
            @PathVariable Long id, @RequestBody EmailTemplate template) {
        return ResponseEntity.ok(templateService.updateTemplate(id, template));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Preview how a template renders with sample variables.
     * The JavaFX template editor calls this endpoint for live preview.
     *
     * Request body: { "name": "Alice", "studentNumber": "ST001", "course": "CS401" }
     */
    @PostMapping("/{id}/preview")
    public ResponseEntity<Map<String, String>> previewTemplate(
            @PathVariable Long id,
            @RequestBody Map<String, String> variables) {
        String rendered = templateService.previewTemplate(id, variables);
        return ResponseEntity.ok(Map.of("rendered", rendered));
    }
}
