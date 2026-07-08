package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.ResponseAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResponseActionRepository extends JpaRepository<ResponseAction, Long> {
    Page<ResponseAction> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<ResponseAction> findByThreatIdOrderByCreatedAtAsc(Long threatId);
}
