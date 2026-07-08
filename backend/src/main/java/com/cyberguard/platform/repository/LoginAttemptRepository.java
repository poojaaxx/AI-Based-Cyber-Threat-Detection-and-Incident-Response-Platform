package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.LoginAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    Page<LoginAttempt> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<LoginAttempt> findByIpAddressAndSuccessFalseAndCreatedAtAfter(String ipAddress, LocalDateTime since);
    long countBySuccessFalseAndCreatedAtAfter(LocalDateTime since);
}
