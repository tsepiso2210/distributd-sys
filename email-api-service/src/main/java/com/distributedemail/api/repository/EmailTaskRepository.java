package com.distributedemail.api.repository;

import com.distributedemail.common.entity.EmailTask;
import com.distributedemail.common.enums.EmailPriority;
import com.distributedemail.common.enums.EmailStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for individual email task queries.
 *
 * Spring Data JPA automatically generates implementations for all methods.
 * Complex queries use @Query with JPQL or native SQL.
 */
@Repository
public interface EmailTaskRepository extends JpaRepository<EmailTask, Long> {

    List<EmailTask> findByCampaign_Id(Long campaignId);

    List<EmailTask> findByStatus(EmailStatus status);

    List<EmailTask> findByPriorityAndStatus(EmailPriority priority, EmailStatus status);

    /** Count tasks by status for dashboard statistics */
    long countByStatus(EmailStatus status);

    /**
     * Find all failed tasks for the "Failed Emails / Retry View" GUI.
     *
     * Uses a named parameter list to avoid JPQL string-literal enum comparison
     * issues across different JPA providers.
     *
     * Assignment requirement: "Failed Emails / Retry View"
     */
    @Query("SELECT t FROM EmailTask t WHERE t.status IN :statuses ORDER BY t.updatedAt DESC")
    List<EmailTask> findByStatusIn(@Param("statuses") List<EmailStatus> statuses);

    /**
     * Convenience method: find all FAILED and DEAD_LETTERED tasks.
     * Called by CampaignService.getFailedTasks() and the Failed Emails GUI view.
     */
    default List<EmailTask> findFailedTasks() {
        return findByStatusIn(List.of(EmailStatus.FAILED, EmailStatus.DEAD_LETTERED));
    }

    /** Count tasks per campaign with a specific status */
    @Query("SELECT COUNT(t) FROM EmailTask t WHERE t.campaign.id = :campaignId AND t.status = :status")
    long countByCampaignAndStatus(@Param("campaignId") Long campaignId,
                                   @Param("status") EmailStatus status);
}
