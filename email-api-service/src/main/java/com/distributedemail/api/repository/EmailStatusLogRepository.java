package com.distributedemail.api.repository;

import com.distributedemail.common.entity.EmailStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailStatusLogRepository extends JpaRepository<EmailStatusLog, Long> {

    /** Retrieve the full status history for a specific email task */
    List<EmailStatusLog> findByEmailTask_IdOrderByTimestampDesc(Long taskId);
}
