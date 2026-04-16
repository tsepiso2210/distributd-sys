package com.distributedemail.api.repository;

import com.distributedemail.common.entity.EmailCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for campaign persistence and querying.
 * Spring Data JPA generates implementations automatically.
 */
@Repository
public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, Long> {

    /** Find campaigns by name (useful for search in the JavaFX GUI) */
    List<EmailCampaign> findByNameContainingIgnoreCase(String name);

    /** Summary query used on the dashboard: most recent campaigns */
    @Query("SELECT c FROM EmailCampaign c ORDER BY c.createdAt DESC")
    List<EmailCampaign> findRecentCampaigns(org.springframework.data.domain.Pageable pageable);
}
