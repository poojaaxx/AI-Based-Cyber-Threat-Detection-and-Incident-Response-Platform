package com.cyberguard.platform.service;

import com.cyberguard.platform.entity.*;
import com.cyberguard.platform.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthenticationMonitoringService {
    private final LoginAttemptRepository attempts;
    private final SecurityEventRepository events;
    private final ThreatService threats;
    private final ObjectMapper mapper;
    private final SseHubService sse;

    // Joins the login transaction: its existing account row lock serializes threshold crossings.
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(LoginAttempt attempt, String outcome, Instant observedAt, boolean thresholdCrossed, int threshold) {
        attempt.setOutcome(outcome);
        attempt = attempts.save(attempt);
        SecurityEvent event = SecurityEvent.builder()
                .loginAttemptId(attempt.getId()).source("CYBERGUARD_AUTH")
                .eventType("LOGIN_ATTEMPT").username(attempt.getUsernameAttempted())
                .sourceIp(attempt.getIpAddress()).outcome(outcome).observedAt(observedAt)
                .ingestedAt(Instant.now()).detectorType("RULE")
                .result("LOCKED".equals(outcome) ? "Account already locked; no new finding" : "No rule matched")
                .build();
        if (thresholdCrossed) {
            var evidenceAttempts = attempts.findByUserIdAndOutcomeOrderByIdDesc(
                    attempt.getUser().getId(), "FAILED", PageRequest.of(0, threshold));
            try {
                event.setEvidence(mapper.writeValueAsString(Map.of(
                        "ruleId", "AUTH_LOCKOUT_V1", "threshold", threshold,
                        "consecutiveFailureCount", attempt.getUser().getFailedLoginAttempts(),
                        "attempts", evidenceAttempts.stream().map(a -> Map.of(
                                "id", a.getId(), "timestamp", a.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toString())).toList())));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Cannot serialize authentication evidence", ex);
            }
            var detection = threats.persistAuthenticationFinding(attempt, event.getEvidence());
            event.setThreatId(detection.getThreat().getId());
            event.setIncidentId(detection.getIncidentId());
            event.setDetectedAt(detection.getThreat().getDetectedAt().atZone(ZoneId.systemDefault()).toInstant());
            event.setResult("Repeated authentication failures / suspected brute force");
        }
        event.setProcessingLatencyMs(Duration.between(observedAt, Instant.now()).toMillis());
        sse.publishSecurityEvent(events.save(event));
    }
}
