package com.distributedemail.api.service;

import com.distributedemail.api.repository.EmailTemplateRepository;
import com.distributedemail.common.entity.EmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * TemplateService - manage email templates with dynamic placeholders.
 *
 * Assignment requirement: "Email templates with dynamic placeholders"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final EmailTemplateRepository templateRepository;

    public List<EmailTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }

    public Optional<EmailTemplate> getTemplateById(Long id) {
        return templateRepository.findById(id);
    }

    @Transactional
    public EmailTemplate createTemplate(EmailTemplate template) {
        log.info("Creating email template: {}", template.getName());
        return templateRepository.save(template);
    }

    @Transactional
    public EmailTemplate updateTemplate(Long id, EmailTemplate updated) {
        EmailTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Template not found: " + id));

        template.setName(updated.getName());
        template.setSubjectTemplate(updated.getSubjectTemplate());
        template.setBodyTemplate(updated.getBodyTemplate());
        template.setDescription(updated.getDescription());

        return templateRepository.save(template);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        templateRepository.deleteById(id);
    }

    /**
     * Preview how a template renders with sample variables.
     * Useful in the JavaFX template editor.
     */
    public String previewTemplate(Long templateId, java.util.Map<String, String> variables) {
        EmailTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new RuntimeException("Template not found: " + templateId));

        String body = template.getBodyTemplate();
        if (variables != null) {
            for (var entry : variables.entrySet()) {
                body = body.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }
        return body;
    }
}
