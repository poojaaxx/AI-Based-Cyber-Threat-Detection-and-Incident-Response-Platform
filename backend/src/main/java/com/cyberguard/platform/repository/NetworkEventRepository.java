package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.NetworkEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkEventRepository extends JpaRepository<NetworkEvent, Long> {
    Page<NetworkEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<NetworkEvent> findByFlaggedTrueOrderByCreatedAtDesc(Pageable pageable);
}
