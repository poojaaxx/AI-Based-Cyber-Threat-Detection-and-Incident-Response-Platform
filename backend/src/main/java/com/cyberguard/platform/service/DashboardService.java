package com.cyberguard.platform.service;

import com.cyberguard.platform.dto.response.DailyTrendPoint;
import com.cyberguard.platform.dto.response.DashboardSummaryResponse;
import com.cyberguard.platform.entity.Threat;
import com.cyberguard.platform.entity.enums.IncidentStatus;
import com.cyberguard.platform.entity.enums.Severity;
import com.cyberguard.platform.entity.enums.ThreatType;
import com.cyberguard.platform.repository.IncidentRepository;
import com.cyberguard.platform.repository.ThreatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ThreatRepository threatRepository;
    private final IncidentRepository incidentRepository;
    private final ThreatService threatService;
    private final IncidentService incidentService;

    public DashboardSummaryResponse getSummary() {
        long activeThreats = threatService.countActiveThreats();
        long criticalThreats = threatService.countBySeverity(Severity.CRITICAL);
        long highSeverityThreats = threatService.countBySeverity(Severity.HIGH);
        long totalThreats = threatRepository.count();
        long totalThreatsToday = threatService.countSince(LocalDate.now().atStartOfDay());
        long totalIncidents = incidentRepository.count();
        long openIncidents = incidentRepository.countByStatus(IncidentStatus.OPEN)
                + incidentRepository.countByStatus(IncidentStatus.ASSIGNED)
                + incidentRepository.countByStatus(IncidentStatus.IN_PROGRESS);

        Map<String, Long> threatsBySeverity = new LinkedHashMap<>();
        for (Severity s : Severity.values()) {
            threatsBySeverity.put(s.name(), threatRepository.countBySeverity(s));
        }

        // Single grouped query instead of one findAll() per ThreatType enum value.
        Map<String, Long> threatsByType = new LinkedHashMap<>();
        for (Object[] row : threatRepository.countGroupedByType()) {
            threatsByType.put(((ThreatType) row[0]).name(), (Long) row[1]);
        }

        Map<String, Long> incidentsByStatus = new LinkedHashMap<>();
        for (IncidentStatus s : IncidentStatus.values()) {
            incidentsByStatus.put(s.name(), incidentRepository.countByStatus(s));
        }

        int securityScore = computeSecurityScore(activeThreats, criticalThreats, openIncidents);

        return DashboardSummaryResponse.builder()
                .securityScore(securityScore)
                .totalThreats(totalThreats)
                .activeThreats(activeThreats)
                .totalThreatsToday(totalThreatsToday)
                .totalIncidents(totalIncidents)
                .openIncidents(openIncidents)
                .criticalThreats(criticalThreats)
                .highSeverityThreats(highSeverityThreats)
                .threatsBySeverity(threatsBySeverity)
                .threatsByType(threatsByType)
                .incidentsByStatus(incidentsByStatus)
                .recentThreats(threatService.getRecentThreats())
                .recentIncidents(incidentService.getRecentIncidents())
                .threatTrend(getThreatTrend(7))
                .build();
    }

    /** Weighted heuristic: starts at 100, deducts for active/critical threats and open incidents. */
    private int computeSecurityScore(long activeThreats, long criticalThreats, long openIncidents) {
        int score = 100;
        score -= Math.min(40, activeThreats * 3);
        score -= Math.min(30, criticalThreats * 8);
        score -= Math.min(20, openIncidents * 4);
        return Math.max(0, (int) score);
    }

    private List<DailyTrendPoint> getThreatTrend(int days) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");
        LocalDateTime since = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        List<Threat> threats = threatService.findSince(since);

        Map<String, Long> grouped = threats.stream().collect(Collectors.groupingBy(
                t -> t.getDetectedAt().toLocalDate().format(formatter), LinkedHashMap::new, Collectors.counting()));

        return java.util.stream.IntStream.range(0, days)
                .mapToObj(i -> LocalDate.now().minusDays(days - 1L - i))
                .map(date -> new DailyTrendPoint(date.format(formatter), grouped.getOrDefault(date.format(formatter), 0L)))
                .collect(Collectors.toList());
    }
}
