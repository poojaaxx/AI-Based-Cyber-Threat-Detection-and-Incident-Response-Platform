package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.IncidentTimeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentTimelineRepository extends JpaRepository<IncidentTimeline, Long> {
    List<IncidentTimeline> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
}
