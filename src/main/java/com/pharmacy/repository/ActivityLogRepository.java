package com.pharmacy.repository;

import com.pharmacy.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findTop200ByOrderByCreatedAtDesc();
    List<ActivityLog> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);
}
