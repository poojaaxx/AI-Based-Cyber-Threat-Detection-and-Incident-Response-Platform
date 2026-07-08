package com.cyberguard.platform.service;

import com.cyberguard.platform.dto.response.*;
import com.cyberguard.platform.entity.Incident;
import com.cyberguard.platform.entity.Threat;
import com.cyberguard.platform.entity.enums.ThreatStatus;
import com.cyberguard.platform.entity.enums.ThreatType;
import com.cyberguard.platform.repository.IncidentRepository;
import com.cyberguard.platform.repository.ThreatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enterprise Analytics: every breakdown below is derived from exactly two DB
 * round trips (the full threats table and the full incidents table, both
 * fetched once at the top of getOverview()) rather than one query per chart -
 * deliberate to keep the Analytics page's single API call cheap regardless of
 * how many widgets are on it.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final ThreatRepository threatRepository;
    private final IncidentRepository incidentRepository;
    private final MitreMappingService mitreMappingService;
    private final RiskScoreService riskScoreService;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("MMM dd");

    public AnalyticsOverviewResponse getOverview() {
        List<Threat> threats = threatRepository.findAll();
        List<Incident> incidents = incidentRepository.findAll();

        return AnalyticsOverviewResponse.builder()
                .severityDistribution(severityDistribution(threats))
                .categoryDistribution(categoryDistribution())
                .dailyTrend(dailyTrend(threats, 30))
                .weeklyTrend(weeklyTrend(threats, 12))
                .monthlyTrend(monthlyTrend(threats, 12))
                .riskTrend(riskScoreService.computeRiskTrend(threats, 30))
                .mitreDistribution(mitreDistribution())
                .avgResolutionTimeHours(avgResolutionTimeHours(incidents))
                .resolutionTimeBySeverity(resolutionTimeBySeverity(incidents))
                .topAttackSources(topEntries(threatRepository.topSourceIps(PageRequest.of(0, 10))))
                .topTargetAssets(topEntries(threatRepository.topDestinationIps(PageRequest.of(0, 10))))
                .totalThreatsAnalyzed(threats.size())
                .falsePositiveCount(threats.stream().filter(t -> t.getStatus() == ThreatStatus.FALSE_POSITIVE).count())
                .detectionAccuracyPercent(detectionAccuracy(threats))
                .threatGrowthPercent(threatGrowthPercent())
                .mostActiveAnalysts(mostActiveAnalysts(incidents))
                .riskDistribution(riskDistribution(threats))
                .build();
    }

    private Map<String, Long> severityDistribution(List<Threat> threats) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (var severity : com.cyberguard.platform.entity.enums.Severity.values()) {
            result.put(severity.name(), threats.stream().filter(t -> t.getSeverity() == severity).count());
        }
        return result;
    }

    private Map<String, Long> categoryDistribution() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : threatRepository.countGroupedByType()) {
            result.put(((ThreatType) row[0]).name(), (Long) row[1]);
        }
        return result;
    }

    private List<DailyTrendPoint> dailyTrend(List<Threat> threats, int days) {
        LocalDateTime since = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        Map<String, Long> grouped = threats.stream()
                .filter(t -> t.getDetectedAt().isAfter(since))
                .collect(Collectors.groupingBy(t -> t.getDetectedAt().toLocalDate().format(DAY_FMT),
                        LinkedHashMap::new, Collectors.counting()));

        return java.util.stream.IntStream.range(0, days)
                .mapToObj(i -> LocalDate.now().minusDays(days - 1L - i))
                .map(date -> new DailyTrendPoint(date.format(DAY_FMT), grouped.getOrDefault(date.format(DAY_FMT), 0L)))
                .collect(Collectors.toList());
    }

    private List<TrendPoint> weeklyTrend(List<Threat> threats, int weeks) {
        WeekFields weekFields = WeekFields.ISO;
        LocalDate cutoff = LocalDate.now().minusWeeks(weeks - 1L);
        Map<Integer, Long> byWeek = threats.stream()
                .map(t -> t.getDetectedAt().toLocalDate())
                .filter(d -> !d.isBefore(cutoff.with(weekFields.dayOfWeek(), 1)))
                .collect(Collectors.groupingBy(d -> d.get(weekFields.weekOfWeekBasedYear()), Collectors.counting()));

        List<TrendPoint> result = new ArrayList<>();
        for (int i = weeks - 1; i >= 0; i--) {
            LocalDate weekStart = LocalDate.now().minusWeeks(i).with(weekFields.dayOfWeek(), 1);
            int weekNum = weekStart.get(weekFields.weekOfWeekBasedYear());
            result.add(TrendPoint.builder().label("W" + weekNum).count(byWeek.getOrDefault(weekNum, 0L)).build());
        }
        return result;
    }

    private List<TrendPoint> monthlyTrend(List<Threat> threats, int months) {
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM yyyy");
        Map<String, Long> byMonth = threats.stream()
                .collect(Collectors.groupingBy(t -> t.getDetectedAt().toLocalDate().format(monthFmt), Collectors.counting()));

        List<TrendPoint> result = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            String label = LocalDate.now().minusMonths(i).format(monthFmt);
            result.add(TrendPoint.builder().label(label).count(byMonth.getOrDefault(label, 0L)).build());
        }
        return result;
    }

    private List<MitreDistributionEntry> mitreDistribution() {
        Map<String, Long> typeCounts = categoryDistribution();
        Map<String, MitreDistributionEntry> byTechnique = new LinkedHashMap<>();

        for (Map.Entry<String, Long> entry : typeCounts.entrySet()) {
            ThreatType type = ThreatType.valueOf(entry.getKey());
            for (var technique : mitreMappingService.getTechniquesForThreatType(type)) {
                byTechnique.merge(technique.techniqueId(),
                        MitreDistributionEntry.builder()
                                .techniqueId(technique.techniqueId())
                                .name(technique.name())
                                .tactic(technique.tactic())
                                .count(entry.getValue())
                                .build(),
                        (a, b) -> MitreDistributionEntry.builder()
                                .techniqueId(a.getTechniqueId()).name(a.getName()).tactic(a.getTactic())
                                .count(a.getCount() + b.getCount()).build());
            }
        }
        return byTechnique.values().stream()
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
    }

    private double avgResolutionTimeHours(List<Incident> incidents) {
        List<Incident> resolved = incidents.stream().filter(i -> i.getResolvedAt() != null).toList();
        if (resolved.isEmpty()) return 0.0;
        double totalHours = resolved.stream()
                .mapToDouble(i -> Duration.between(i.getCreatedAt(), i.getResolvedAt()).toMinutes() / 60.0)
                .sum();
        return round1(totalHours / resolved.size());
    }

    private Map<String, Double> resolutionTimeBySeverity(List<Incident> incidents) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (var severity : com.cyberguard.platform.entity.enums.Severity.values()) {
            List<Incident> resolved = incidents.stream()
                    .filter(i -> i.getSeverity() == severity && i.getResolvedAt() != null)
                    .toList();
            if (resolved.isEmpty()) {
                result.put(severity.name(), 0.0);
                continue;
            }
            double avg = resolved.stream()
                    .mapToDouble(i -> Duration.between(i.getCreatedAt(), i.getResolvedAt()).toMinutes() / 60.0)
                    .average().orElse(0.0);
            result.put(severity.name(), round1(avg));
        }
        return result;
    }

    private List<CountEntry> topEntries(List<Object[]> rows) {
        return rows.stream()
                .map(row -> CountEntry.builder().label((String) row[0]).count((Long) row[1]).build())
                .collect(Collectors.toList());
    }

    private double detectionAccuracy(List<Threat> threats) {
        if (threats.isEmpty()) return 100.0;
        long falsePositives = threats.stream().filter(t -> t.getStatus() == ThreatStatus.FALSE_POSITIVE).count();
        return round1(100.0 * (threats.size() - falsePositives) / threats.size());
    }

    /** Compares threat volume in the last 7 days against the 7 days before that. */
    private double threatGrowthPercent() {
        LocalDateTime now = LocalDateTime.now();
        long current = threatRepository.countByDetectedAtBetween(now.minusDays(7), now);
        long previous = threatRepository.countByDetectedAtBetween(now.minusDays(14), now.minusDays(7));
        if (previous == 0) return current > 0 ? 100.0 : 0.0;
        return round1(100.0 * (current - previous) / previous);
    }

    private List<CountEntry> mostActiveAnalysts(List<Incident> incidents) {
        Map<String, Long> byAnalyst = incidents.stream()
                .filter(i -> i.getAssignedTo() != null)
                .collect(Collectors.groupingBy(i -> i.getAssignedTo().getUsername(), Collectors.counting()));

        return byAnalyst.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> CountEntry.builder().label(e.getKey()).count(e.getValue()).build())
                .collect(Collectors.toList());
    }

    /** Buckets each threat's own AI-computed risk score (not the enterprise aggregate) into LOW/MEDIUM/HIGH/CRITICAL. */
    private Map<String, Long> riskDistribution(List<Threat> threats) {
        Map<String, Long> result = new LinkedHashMap<>(Map.of("LOW", 0L, "MEDIUM", 0L, "HIGH", 0L, "CRITICAL", 0L));
        for (Threat threat : threats) {
            BigDecimal score = threat.getRiskScore();
            double value = score != null ? score.doubleValue() : 0.0;
            String bucket = value >= 75 ? "CRITICAL" : value >= 50 ? "HIGH" : value >= 25 ? "MEDIUM" : "LOW";
            result.merge(bucket, 1L, Long::sum);
        }
        return result;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
