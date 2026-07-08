package com.cyberguard.platform.dto.response;

import com.cyberguard.platform.entity.AuditLog;
import com.cyberguard.platform.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityHealthResponse {
    private long failedLoginAttempts24h;
    private long lockedAccounts;
    private long disabledAccounts;
    private long activeUsers;
    private long onlineUsers;
    private Map<String, Long> roleDistribution;

    private long criticalThreatCount;
    private double averageRiskScore;
    private String riskLevel;
    private double averageIncidentResolutionHours;
    private double threatDetectionRatePerDay;
    private String mostCommonThreatCategory;

    private long auditEvents24h;
    private Map<String, Long> auditLogSummary;
    private List<AuditLog> recentUserActivity;
    private List<AuditLog> recentAdminActivity;
    private List<Notification> recentSecurityEvents;

    private long jwtActiveTokens;
    private long jwtExpiringSoon;

    private SystemHealthInfo systemHealth;
}
