package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.Threat;
import com.cyberguard.platform.entity.enums.Severity;
import com.cyberguard.platform.entity.enums.ThreatStatus;
import com.cyberguard.platform.entity.enums.ThreatType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ThreatRepository extends JpaRepository<Threat, Long>, JpaSpecificationExecutor<Threat> {
    Page<Threat> findByStatus(ThreatStatus status, Pageable pageable);
    Page<Threat> findBySeverity(Severity severity, Pageable pageable);
    Page<Threat> findByThreatType(ThreatType type, Pageable pageable);
    List<Threat> findTop10ByOrderByDetectedAtDesc();
    long countByStatus(ThreatStatus status);
    long countBySeverity(Severity severity);
    long countByDetectedAtAfter(LocalDateTime since);
    long countByDetectedAtBetween(LocalDateTime start, LocalDateTime end);
    List<Threat> findByDetectedAtAfter(LocalDateTime since);

    /** Single grouped query - replaces looping findAll() once per ThreatType enum value. */
    @Query("select t.threatType, count(t) from Threat t group by t.threatType")
    List<Object[]> countGroupedByType();

    @Query("select t.sourceIp, count(t) from Threat t where t.sourceIp is not null group by t.sourceIp order by count(t) desc")
    List<Object[]> topSourceIps(Pageable pageable);

    @Query("select t.destinationIp, count(t) from Threat t where t.destinationIp is not null group by t.destinationIp order by count(t) desc")
    List<Object[]> topDestinationIps(Pageable pageable);
}
