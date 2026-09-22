package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {
    Page<SecurityEvent> findBySourceOrderByIdDesc(String source, Pageable pageable);
    Page<SecurityEvent> findAllByOrderByIdDesc(Pageable pageable);
}
