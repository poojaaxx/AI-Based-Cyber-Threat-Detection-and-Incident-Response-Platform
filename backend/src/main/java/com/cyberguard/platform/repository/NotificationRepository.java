package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.Notification;
import com.cyberguard.platform.entity.enums.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<Notification> findByUserIdAndIsReadFalse(Long userId);
    long countByUserIdAndIsReadFalse(Long userId);

    /** Recent security-relevant events across all users, for the Security Health page. */
    List<Notification> findTop10BySeverityInOrderByCreatedAtDesc(List<Severity> severities);
}
