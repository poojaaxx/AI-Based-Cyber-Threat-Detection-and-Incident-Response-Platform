package com.cyberguard.platform.service;

import com.cyberguard.platform.dto.response.RiskScoreResponse;
import com.cyberguard.platform.dto.response.SecurityHealthResponse;
import com.cyberguard.platform.dto.response.SystemHealthInfo;
import com.cyberguard.platform.entity.AuditLog;
import com.cyberguard.platform.entity.Incident;
import com.cyberguard.platform.entity.Notification;
import com.cyberguard.platform.entity.Role;
import com.cyberguard.platform.entity.Threat;
import com.cyberguard.platform.entity.enums.Severity;
import com.cyberguard.platform.entity.enums.UserStatus;
import com.cyberguard.platform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Security Health Dashboard: authentication health, account status, current
 * risk posture, audit activity, and basic system reachability - all read-only,
 * composed almost entirely from repositories/services that already exist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityHealthService {

    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;
    private final ThreatRepository threatRepository;
    private final IncidentRepository incidentRepository;
    private final RiskScoreService riskScoreService;
    private final WebClient aiServiceWebClient;

    public SecurityHealthResponse getSecurityHealth() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since24h = now.minusHours(24);

        List<Threat> threats = threatRepository.findAll();
        List<Incident> incidents = incidentRepository.findAll();
        RiskScoreResponse risk = riskScoreService.computeRiskScore();

        return SecurityHealthResponse.builder()
                .failedLoginAttempts24h(loginAttemptRepository.countBySuccessFalseAndCreatedAtAfter(since24h))
                .lockedAccounts(userRepository.findAll().stream().filter(u -> u.getStatus() == UserStatus.LOCKED).count())
                .disabledAccounts(userRepository.findAll().stream().filter(u -> u.getStatus() == UserStatus.DISABLED).count())
                .activeUsers(userRepository.findAll().stream().filter(u -> u.getStatus() == UserStatus.ACTIVE).count())
                .onlineUsers(refreshTokenRepository.countDistinctActiveUsers(now))
                .roleDistribution(roleDistribution())
                .criticalThreatCount(threats.stream().filter(t -> t.getSeverity() == Severity.CRITICAL).count())
                .averageRiskScore(risk.getOverallRisk())
                .riskLevel(risk.getRiskLevel())
                .averageIncidentResolutionHours(averageResolutionTimeHours(incidents))
                .threatDetectionRatePerDay(threatDetectionRatePerDay(threats))
                .mostCommonThreatCategory(mostCommonThreatCategory(threats))
                .auditEvents24h(auditLogRepository.countByCreatedAtAfter(since24h))
                .auditLogSummary(auditLogSummary(since24h))
                .recentUserActivity(auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10)).getContent())
                .recentAdminActivity(auditLogRepository.findRecentByUserRole(Role.ADMIN, PageRequest.of(0, 10)))
                .recentSecurityEvents(notificationRepository.findTop10BySeverityInOrderByCreatedAtDesc(
                        List.of(Severity.HIGH, Severity.CRITICAL)))
                .jwtActiveTokens(refreshTokenRepository.countByRevokedFalseAndExpiryDateAfter(now))
                .jwtExpiringSoon(refreshTokenRepository.countByRevokedFalseAndExpiryDateBetween(now, now.plusHours(24)))
                .systemHealth(systemHealth())
                .build();
    }

    private Map<String, Long> roleDistribution() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("ADMIN", userRepository.countByRoles_Name(Role.ADMIN));
        result.put("ANALYST", userRepository.countByRoles_Name(Role.ANALYST));
        result.put("USER", userRepository.countByRoles_Name(Role.USER));
        return result;
    }

    private double averageResolutionTimeHours(List<Incident> incidents) {
        List<Incident> resolved = incidents.stream().filter(i -> i.getResolvedAt() != null).toList();
        if (resolved.isEmpty()) return 0.0;
        double totalHours = resolved.stream()
                .mapToDouble(i -> java.time.Duration.between(i.getCreatedAt(), i.getResolvedAt()).toMinutes() / 60.0)
                .sum();
        return round1(totalHours / resolved.size());
    }

    private double threatDetectionRatePerDay(List<Threat> threats) {
        if (threats.isEmpty()) return 0.0;
        LocalDateTime earliest = threats.stream().map(Threat::getDetectedAt).min(LocalDateTime::compareTo).orElse(LocalDateTime.now());
        long daysSpan = Math.max(1, java.time.Duration.between(earliest, LocalDateTime.now()).toDays());
        return round1((double) threats.size() / daysSpan);
    }

    private String mostCommonThreatCategory(List<Threat> threats) {
        return threats.stream()
                .collect(Collectors.groupingBy(t -> t.getThreatType().name(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");
    }

    private Map<String, Long> auditLogSummary(LocalDateTime since) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : auditLogRepository.countGroupedByActionSince(since)) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }

    private SystemHealthInfo systemHealth() {
        String dbStatus;
        try {
            userRepository.count();
            dbStatus = "UP";
        } catch (Exception ex) {
            dbStatus = "DOWN";
        }

        String aiStatus;
        try {
            aiServiceWebClient.get().uri("/health").retrieve().bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(2)).block();
            aiStatus = "UP";
        } catch (Exception ex) {
            log.warn("AI service health check failed: {}", ex.getMessage());
            aiStatus = "DOWN";
        }

        return SystemHealthInfo.builder()
                .backendStatus("UP")
                .databaseStatus(dbStatus)
                .aiServiceStatus(aiStatus)
                .uptimeSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000)
                .build();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
