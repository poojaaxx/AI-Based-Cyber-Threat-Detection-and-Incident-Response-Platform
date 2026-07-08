package com.cyberguard.platform.repository;

import com.cyberguard.platform.entity.ApiConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiConfigurationRepository extends JpaRepository<ApiConfiguration, Long> {
    Optional<ApiConfiguration> findByUserId(Long userId);
}
