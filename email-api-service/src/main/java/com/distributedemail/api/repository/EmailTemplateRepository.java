package com.distributedemail.api.repository;

import com.distributedemail.common.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    Optional<EmailTemplate> findByName(String name);

    List<EmailTemplate> findByNameContainingIgnoreCase(String name);
}
