package com.cyberguard.platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Single consolidated payload for the Analytics page - deliberately one round trip
 * rather than one call per chart, computed from a small, fixed number of DB queries
 * (see AnalyticsService) regardless of how many breakdowns are included here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsOverviewResponse {
    private Map<String, Long> severityDistribution;
    private Map<String, Long> categoryDistribution;
    private List<DailyTrendPoint> dailyTrend;
    private List<TrendPoint> weeklyTrend;
    private List<TrendPoint> monthlyTrend;
    private List<RiskTrendPoint> riskTrend;
    private List<MitreDistributionEntry> mitreDistribution;
    private double avgResolutionTimeHours;
    private Map<String, Double> resolutionTimeBySeverity;
    private List<CountEntry> topAttackSources;
    private List<CountEntry> topTargetAssets;
    private long totalThreatsAnalyzed;
    private long falsePositiveCount;
    private double detectionAccuracyPercent;
    private double threatGrowthPercent;
    private List<CountEntry> mostActiveAnalysts;
    private Map<String, Long> riskDistribution;
}
