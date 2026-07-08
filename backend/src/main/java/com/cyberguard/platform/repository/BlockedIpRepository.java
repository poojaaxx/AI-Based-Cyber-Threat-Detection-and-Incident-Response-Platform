package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.BlockedIp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlockedIpRepository extends JpaRepository<BlockedIp, Long> {
    Optional<BlockedIp> findByIpAddress(String ipAddress);
    boolean existsByIpAddressAndActiveTrue(String ipAddress);
    List<BlockedIp> findByActiveTrue();
}
