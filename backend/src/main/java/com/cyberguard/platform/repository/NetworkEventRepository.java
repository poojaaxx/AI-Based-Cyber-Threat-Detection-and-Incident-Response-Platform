package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.NetworkEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkEventRepository extends JpaRepository<NetworkEvent, Long> {
    boolean existsByCollectorIdAndSessionIdAndSequence(String collectorId, String sessionId, Long sequence);
    boolean existsByCollectorIdAndSessionIdAndConnectionIdAndRuleIdIsNotNull(String collectorId, String sessionId, String connectionId);
    boolean existsByCollectorIdAndSessionIdAndConnectionIdAndEventType(String collectorId, String sessionId, String connectionId, String eventType);
    Page<NetworkEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<NetworkEvent> findByFlaggedTrueOrderByCreatedAtDesc(Pageable pageable);
}
