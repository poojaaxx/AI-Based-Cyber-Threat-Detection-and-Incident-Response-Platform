package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.Incident;
import com.cyberguard.platform.entity.enums.IncidentStatus;
import com.cyberguard.platform.entity.enums.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {
    Optional<Incident> findByIncidentNumber(String incidentNumber);
    Optional<Incident> findByThreatId(Long threatId);
    Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);
    List<Incident> findTop10ByOrderByCreatedAtDesc();
    long countByStatus(IncidentStatus status);
    long countBySeverity(Severity severity);
    long count();
}
