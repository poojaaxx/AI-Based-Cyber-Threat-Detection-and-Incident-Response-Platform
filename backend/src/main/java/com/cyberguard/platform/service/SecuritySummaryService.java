package com.cyberguard.platform.service;

import com.cyberguard.platform.dto.response.RiskScoreResponse;
import com.cyberguard.platform.dto.response.SecuritySummaryResponse;
import com.cyberguard.platform.entity.Threat;
import com.cyberguard.platform.entity.enums.Severity;
import com.cyberguard.platform.entity.enums.ThreatStatus;
import com.cyberguard.platform.repository.IncidentRepository;
import com.cyberguard.platform.repository.ThreatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates a natural-language security posture summary from the same live
 * data backing the dashboard and risk score widgets - a templated (not LLM-
 * generated) narrative, consistent with the rule-based approach already used
 * by the AI Security Assistant, so it needs no external API/key and always
 * reflects the real current state of the database.
 */
@Service
@RequiredArgsConstructor
public class SecuritySummaryService {

    private final ThreatRepository threatRepository;
    private final IncidentRepository incidentRepository;
    private final RiskScoreService riskScoreService;

    private static final Map<String, String> RECOMMENDATION_BY_CATEGORY = Map.of(
            "network", "Review firewall rules and enable rate-limiting for internet-facing services.",
            "host", "Prioritize endpoint detection & response (EDR) coverage and outstanding OS/software patching.",
            "identity", "Enforce multi-factor authentication and review recent failed login attempts.",
            "application", "Audit web application input validation and review Web Application Firewall (WAF) policies.",
            "data", "Verify backup integrity/immutability and review data access permissions."
    );

    public SecuritySummaryResponse generateSummary() {
        List<Threat> allThreats = threatRepository.findAll();
        List<Threat> last7Days = threatRepository.findByDetectedAtAfter(LocalDate.now().minusDays(7).atStartOfDay());

        long activeThreats = threatRepository.countByStatus(ThreatStatus.DETECTED)
                + threatRepository.countByStatus(ThreatStatus.ANALYZING);
        long criticalThreats = threatRepository.countBySeverity(Severity.CRITICAL);
        long mitigatedCount = allThreats.stream().filter(t -> t.getStatus() == ThreatStatus.MITIGATED).count();
        long openIncidents = incidentRepository.countByStatus(com.cyberguard.platform.entity.enums.IncidentStatus.OPEN)
                + incidentRepository.countByStatus(com.cyberguard.platform.entity.enums.IncidentStatus.ASSIGNED)
                + incidentRepository.countByStatus(com.cyberguard.platform.entity.enums.IncidentStatus.IN_PROGRESS);

        RiskScoreResponse risk = riskScoreService.computeRiskScore();
        String postureLevel = switch (risk.getRiskLevel()) {
            case "CRITICAL" -> "CRITICAL";
            case "HIGH" -> "ELEVATED";
            case "MEDIUM" -> "FAIR";
            default -> "GOOD";
        };

        String mostCommonType = last7Days.stream()
                .collect(Collectors.groupingBy(t -> t.getThreatType().name(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        double mitigatedPct = allThreats.isEmpty() ? 0 : Math.round(mitigatedCount * 1000.0 / allThreats.size()) / 10.0;

        StringBuilder sb = new StringBuilder();
        List<String> findings = new ArrayList<>();

        if (activeThreats == 0 && criticalThreats == 0 && openIncidents == 0 && allThreats.isEmpty()) {
            sb.append("No threats have been detected yet. The security posture is GOOD with an overall risk ")
                    .append("score of ").append(risk.getOverallRisk()).append("/100. The AI threat detection engine ")
                    .append("is active and monitoring for malware, DDoS, SQL injection, XSS, brute force, port scan, ")
                    .append("phishing, ransomware, and insider threat activity.");
            findings.add("No active threats or open incidents");
        } else {
            sb.append("The organization's current security posture is ").append(postureLevel)
                    .append(" with an overall risk score of ").append(risk.getOverallRisk())
                    .append("/100 (").append(risk.getRiskLevel()).append("). ");

            sb.append("Over the last 7 days, ").append(last7Days.size())
                    .append(last7Days.size() == 1 ? " threat was detected" : " threats were detected");
            if (mostCommonType != null) {
                sb.append(", most frequently classified as ").append(mostCommonType);
            }
            sb.append(". ");

            sb.append("There ").append(activeThreats == 1 ? "is " : "are ").append(activeThreats)
                    .append(activeThreats == 1 ? " active threat" : " active threats")
                    .append(" requiring attention, including ").append(criticalThreats)
                    .append(" classified as CRITICAL severity. ");
            findings.add(activeThreats + " active threat(s), " + criticalThreats + " critical");

            sb.append(openIncidents).append(openIncidents == 1 ? " incident remains open" : " incidents remain open")
                    .append(" for investigation. ");
            findings.add(openIncidents + " open incident(s)");

            sb.append("Automated response actions have mitigated ").append(mitigatedPct)
                    .append("% of all detected threats without manual intervention. ");
            findings.add(mitigatedPct + "% auto-mitigated");

            String topCategory = topRiskCategory(risk);
            double topScore = categoryScore(risk, topCategory);
            if (topScore >= 25) {
                sb.append("The highest risk domain is ").append(topCategory).append(" risk at ")
                        .append(topScore).append("/100");
                List<String> contributors = risk.getTopContributors().get(topCategory);
                if (contributors != null && !contributors.isEmpty()) {
                    sb.append(", driven primarily by ").append(String.join(", ", contributors));
                }
                sb.append(". ").append(RECOMMENDATION_BY_CATEGORY.getOrDefault(topCategory, ""));
                findings.add("Top risk domain: " + topCategory + " (" + topScore + "/100)");
            }
        }

        return SecuritySummaryResponse.builder()
                .postureLevel(postureLevel)
                .summary(sb.toString().trim())
                .keyFindings(findings)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private String topRiskCategory(RiskScoreResponse risk) {
        Map<String, Double> scores = Map.of(
                "network", risk.getNetworkRisk(), "host", risk.getHostRisk(), "identity", risk.getIdentityRisk(),
                "application", risk.getApplicationRisk(), "data", risk.getDataRisk()
        );
        return scores.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("network");
    }

    private double categoryScore(RiskScoreResponse risk, String category) {
        return switch (category) {
            case "network" -> risk.getNetworkRisk();
            case "host" -> risk.getHostRisk();
            case "identity" -> risk.getIdentityRisk();
            case "application" -> risk.getApplicationRisk();
            case "data" -> risk.getDataRisk();
            default -> 0;
        };
    }
}
