package com.distributedemail.worker.repository;

import com.distributedemail.common.entity.EmailTask;
import com.distributedemail.common.enums.EmailStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Worker's view of the email_tasks table.
 *
 * The worker uses this repository to:
 *   - Load a task by its primary key (for idempotency check before sending)
 *   - Update the task status after each send attempt
 *   - Track retry count and last error message
 *
 * Assignment requirement: "Worker updates task status in PostgreSQL after each send attempt"
 */
@Repository
public interface EmailTaskRepository extends JpaRepository<EmailTask, Long> {

    /** Find tasks by status — used for monitoring and recovery */
    List<EmailTask> findByStatus(EmailStatus status);

    /** Count tasks with a given status — used for health monitoring */
    long countByStatus(EmailStatus status);

    /**
     * Lightweight status update that bypasses entity loading.
     * Called after idempotency check, in high-throughput path.
     */
    @Modifying
    @Query("UPDATE EmailTask t SET t.status = :status WHERE t.id = :taskId")
    int updateTaskStatus(@Param("taskId") Long taskId, @Param("status") EmailStatus status);
}
