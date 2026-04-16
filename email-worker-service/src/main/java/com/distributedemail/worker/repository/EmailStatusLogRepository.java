package com.distributedemail.worker.repository;

import com.distributedemail.common.entity.EmailStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailStatusLogRepository extends JpaRepository<EmailStatusLog, Long> {
}
