package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.CveRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CveRecordRepository extends JpaRepository<CveRecord, Long> {
    Optional<CveRecord> findByCveId(String cveId);
    Page<CveRecord> findByTitleContainingIgnoreCaseOrCveIdContainingIgnoreCase(String title, String cveId, Pageable pageable);
}
