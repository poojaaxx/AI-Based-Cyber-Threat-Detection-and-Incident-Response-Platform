package com.cyberguard.platform.dto.response;

import com.cyberguard.platform.entity.Incident;
import com.cyberguard.platform.entity.Threat;
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
public class DashboardSummaryResponse {
    private int securityScore;
    private long totalThreats;
    private long activeThreats;
    private long totalThreatsToday;
    private long totalIncidents;
    private long openIncidents;
    private long criticalThreats;
    private long highSeverityThreats;
    private Map<String, Long> threatsBySeverity;
    private Map<String, Long> threatsByType;
    private Map<String, Long> incidentsByStatus;
    private List<Threat> recentThreats;
    private List<Incident> recentIncidents;
    private List<DailyTrendPoint> threatTrend;
}
