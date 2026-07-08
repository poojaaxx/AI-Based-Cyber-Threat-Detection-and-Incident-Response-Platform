package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.SystemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
    Page<SystemLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
