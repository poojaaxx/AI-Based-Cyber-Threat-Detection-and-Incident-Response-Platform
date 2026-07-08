package com.cyberguard.platform.service;

import com.cyberguard.platform.dto.response.RiskScoreResponse;
import com.cyberguard.platform.dto.response.RiskTrendPoint;
import com.cyberguard.platform.entity.Threat;
import com.cyberguard.platform.entity.enums.NotificationType;
import com.cyberguard.platform.entity.enums.Severity;
import com.cyberguard.platform.entity.enums.ThreatStatus;
import com.cyberguard.platform.entity.enums.ThreatType;
import com.cyberguard.platform.repository.ThreatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enterprise Risk Score: computes an overall risk figure plus a breakdown
 * across network / host / identity / application / data domains, derived
 * entirely from the threats currently in the database (type, severity,
 * confidence, and whether they've already been mitigated).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskScoreService {

    private final ThreatRepository threatRepository;
    private final NotificationService notificationService;
    private final SseHubService sseHubService;

    /** Last-seen risk level, used purely to detect changes for the "AI Risk Score Updated" live event. */
    private volatile String lastKnownRiskLevel;

    private static final Map<Severity, Double> SEVERITY_WEIGHT = Map.of(
            Severity.LOW, 25.0, Severity.MEDIUM, 50.0, Severity.HIGH, 75.0, Severity.CRITICAL, 100.0
    );

    /** Each threat type distributes its "weight of concern" across one or more risk domains. */
    private static final Map<ThreatType, Map<String, Double>> CATEGORY_WEIGHTS = Map.ofEntries(
            Map.entry(ThreatType.MALWARE, Map.of("host", 0.7, "network", 0.3)),
            Map.entry(ThreatType.DDOS, Map.of("network", 1.0)),
            Map.entry(ThreatType.SQL_INJECTION, Map.of("application", 0.7, "data", 0.3)),
            Map.entry(ThreatType.XSS, Map.of("application", 1.0)),
            Map.entry(ThreatType.BRUTE_FORCE, Map.of("identity", 1.0)),
            Map.entry(ThreatType.PORT_SCAN, Map.of("network", 1.0)),
            Map.entry(ThreatType.PHISHING, Map.of("identity", 0.6, "application", 0.4)),
            Map.entry(ThreatType.RANSOMWARE, Map.of("host", 0.5, "data", 0.5)),
            Map.entry(ThreatType.INSIDER_THREAT, Map.of("identity", 0.5, "data", 0.5)),
            Map.entry(ThreatType.UNKNOWN, Map.of("network", 0.1, "host", 0.1, "identity", 0.1, "application", 0.1, "data", 0.1))
    );

    private static final List<String> CATEGORIES = List.of("network", "host", "identity", "application", "data");

    public RiskScoreResponse computeRiskScore() {
        return computeFromThreats(threatRepository.findAll());
    }

    /**
     * 30-day Enterprise Risk Trend. Fetches the threat table exactly once, then re-scores it
     * as-of each of the last {@code days} cutoffs entirely in memory - avoids the naive approach
     * of re-querying the database once per day on the chart.
     */
    public List<RiskTrendPoint> computeRiskTrend(int days) {
        return computeRiskTrend(threatRepository.findAll(), days);
    }

    /** Overload for callers (e.g. AnalyticsService) that already fetched the threat table once. */
    public List<RiskTrendPoint> computeRiskTrend(List<Threat> allThreats, int days) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");
        List<RiskTrendPoint> trend = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime cutoff = LocalDate.now().minusDays(i).atTime(23, 59, 59);
            List<Threat> asOf = allThreats.stream()
                    .filter(t -> !t.getDetectedAt().isAfter(cutoff))
                    .collect(Collectors.toList());
            RiskScoreResponse scored = computeFromThreats(asOf);
            trend.add(RiskTrendPoint.builder()
                    .date(cutoff.toLocalDate().format(formatter))
                    .overallRisk(scored.getOverallRisk())
                    .riskLevel(scored.getRiskLevel())
                    .build());
        }
        return trend;
    }

    private RiskScoreResponse computeFromThreats(List<Threat> threats) {
        Map<String, Double> rawScores = new HashMap<>();
        Map<String, Map<String, Integer>> contributorCounts = new HashMap<>();
        for (String category : CATEGORIES) {
            rawScores.put(category, 0.0);
            contributorCounts.put(category, new LinkedHashMap<>());
        }

        for (Threat threat : threats) {
            double statusWeight = switch (threat.getStatus()) {
                case DETECTED, ANALYZING -> 1.0;
                case MITIGATED -> 0.3;
                case FALSE_POSITIVE, IGNORED -> 0.0;
            };
            if (statusWeight == 0.0) continue;

            double severityWeight = SEVERITY_WEIGHT.getOrDefault(threat.getSeverity(), 25.0);
            double confidenceFactor = threat.getConfidenceScore() != null
                    ? threat.getConfidenceScore().doubleValue() / 100.0 : 0.5;
            double contribution = (severityWeight * confidenceFactor * statusWeight) / 100.0;

            Map<String, Double> weights = CATEGORY_WEIGHTS.getOrDefault(threat.getThreatType(), Map.of());
            for (Map.Entry<String, Double> entry : weights.entrySet()) {
                String category = entry.getKey();
                double weighted = contribution * entry.getValue();
                rawScores.merge(category, weighted, Double::sum);
                if (weighted > 0.05) {
                    contributorCounts.get(category).merge(threat.getThreatType().name(), 1, Integer::sum);
                }
            }
        }

        Map<String, Double> categoryScores = new HashMap<>();
        for (String category : CATEGORIES) {
            categoryScores.put(category, saturate(rawScores.get(category)));
        }

        double maxCategory = categoryScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double meanCategory = categoryScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double overall = round1(0.6 * maxCategory + 0.4 * meanCategory);

        Map<String, List<String>> topContributors = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            List<String> formatted = contributorCounts.get(category).entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .map(e -> e.getKey() + " (" + e.getValue() + ")")
                    .toList();
            topContributors.put(category, formatted);
        }

        return RiskScoreResponse.builder()
                .overallRisk(overall)
                .riskLevel(riskLevel(overall))
                .networkRisk(round1(categoryScores.get("network")))
                .hostRisk(round1(categoryScores.get("host")))
                .identityRisk(round1(categoryScores.get("identity")))
                .applicationRisk(round1(categoryScores.get("application")))
                .dataRisk(round1(categoryScores.get("data")))
                .topContributors(topContributors)
                .build();
    }

    /**
     * Periodically recomputes the enterprise risk level and, when it changes since the last
     * check, notifies admins/analysts and broadcasts a dashboard-update signal. Runs on the
     * existing @EnableScheduling infrastructure already active on the application - no new
     * scheduling configuration required.
     */
    @Scheduled(fixedRate = 60000)
    public void monitorRiskLevelChange() {
        try {
            String currentLevel = computeRiskScore().getRiskLevel();
            if (lastKnownRiskLevel != null && !lastKnownRiskLevel.equals(currentLevel)) {
                Severity severity = switch (currentLevel) {
                    case "CRITICAL" -> Severity.CRITICAL;
                    case "HIGH" -> Severity.HIGH;
                    case "MEDIUM" -> Severity.MEDIUM;
                    default -> Severity.LOW;
                };
                notificationService.notifyAllAdmins("AI Risk Score Updated",
                        "Enterprise risk level changed from " + lastKnownRiskLevel + " to " + currentLevel,
                        NotificationType.SYSTEM, severity, "risk-score");
                sseHubService.broadcastDashboardUpdate("RISK_SCORE_UPDATED");
            }
            lastKnownRiskLevel = currentLevel;
        } catch (Exception ex) {
            log.warn("Risk level monitor failed: {}", ex.getMessage());
        }
    }

    /** Diminishing-returns curve: a single severe incident raises risk noticeably without instantly maxing it out. */
    private double saturate(double rawScore) {
        return 100.0 * (1 - Math.exp(-0.5 * rawScore));
    }

    private String riskLevel(double overall) {
        if (overall >= 75) return "CRITICAL";
        if (overall >= 50) return "HIGH";
        if (overall >= 25) return "MEDIUM";
        return "LOW";
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
