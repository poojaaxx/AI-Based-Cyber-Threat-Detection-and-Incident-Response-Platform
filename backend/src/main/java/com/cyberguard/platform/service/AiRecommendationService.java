package com.cyberguard.platform.service;

import com.cyberguard.platform.entity.enums.Severity;
import com.cyberguard.platform.entity.enums.ThreatType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic ThreatType -> recommended response actions, mirroring the same
 * static-map pattern used by MitreMappingService. Severity CRITICAL/HIGH always
 * appends "Create Incident" and "Notify Security Team" if not already present.
 */
@Service
public class AiRecommendationService {

    private static final Map<ThreatType, List<String>> BASE_RECOMMENDATIONS = Map.ofEntries(
            Map.entry(ThreatType.MALWARE, List.of("Isolate Endpoint", "Increase Monitoring", "Review Firewall Rules")),
            Map.entry(ThreatType.DDOS, List.of("Review Firewall Rules", "Increase Monitoring")),
            Map.entry(ThreatType.SQL_INJECTION, List.of("Review Firewall Rules", "Investigate Lateral Movement")),
            Map.entry(ThreatType.XSS, List.of("Review Firewall Rules", "Increase Monitoring")),
            Map.entry(ThreatType.BRUTE_FORCE, List.of("Block Source IP", "Reset Credentials", "Enable MFA")),
            Map.entry(ThreatType.PORT_SCAN, List.of("Block Source IP", "Increase Monitoring")),
            Map.entry(ThreatType.PHISHING, List.of("Reset Credentials", "Enable MFA")),
            Map.entry(ThreatType.RANSOMWARE, List.of("Isolate Endpoint", "Block Source IP")),
            Map.entry(ThreatType.INSIDER_THREAT, List.of("Investigate Lateral Movement", "Increase Monitoring")),
            Map.entry(ThreatType.UNKNOWN, List.of("Increase Monitoring"))
    );

    public List<String> getRecommendations(ThreatType threatType, Severity severity) {
        List<String> recommendations = new ArrayList<>(
                BASE_RECOMMENDATIONS.getOrDefault(threatType, List.of("Increase Monitoring")));

        if (severity == Severity.CRITICAL || severity == Severity.HIGH) {
            if (!recommendations.contains("Notify Security Team")) recommendations.add("Notify Security Team");
            if (!recommendations.contains("Create Incident")) recommendations.add("Create Incident");
        }
        return recommendations;
    }
}
