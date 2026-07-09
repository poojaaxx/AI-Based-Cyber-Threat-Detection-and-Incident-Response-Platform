package com.cyberguard.platform.service;

import com.cyberguard.platform.client.AiServiceClient;
import com.cyberguard.platform.dto.request.ThreatDetectionRequest;
import com.cyberguard.platform.dto.response.AiPredictionResponse;
import com.cyberguard.platform.dto.response.ThreatDetectionResponse;
import com.cyberguard.platform.entity.Incident;
import com.cyberguard.platform.entity.Threat;
import com.cyberguard.platform.entity.User;
import com.cyberguard.platform.entity.enums.Severity;
import com.cyberguard.platform.entity.enums.ThreatStatus;
import com.cyberguard.platform.entity.enums.ThreatType;
import com.cyberguard.platform.exception.ResourceNotFoundException;
import com.cyberguard.platform.repository.ThreatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThreatService {

    private final ThreatRepository threatRepository;
    private final AiServiceClient aiServiceClient;
    private final ResponseActionService responseActionService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final SseHubService sseHubService;

    /**
     * Core AI threat detection pipeline: forwards raw event features to the
     * AI microservice, persists the classified threat, then triggers the
     * automated incident response playbook for high-severity results.
     *
     * @param actor the authenticated user who triggered this detection (e.g. via the
     *              Simulate Threat page or an external ingestion integration); used
     *              only for audit logging, never for the automated response actions
     *              (which remain attributed to the system, not a specific user).
     */
    @Transactional
    public ThreatDetectionResponse detectAndPersist(ThreatDetectionRequest request, User actor) {
        AiPredictionResponse prediction = aiServiceClient.predictThreat(request);

        Threat threat = Threat.builder()
                .threatType(parseThreatType(prediction.getThreatType()))
                .severity(parseSeverity(prediction.getSeverity()))
                .confidenceScore(BigDecimal.valueOf(prediction.getConfidenceScore() != null ? prediction.getConfidenceScore() * 100 : 0))
                .sourceIp(request.getSourceIp())
                .destinationIp(request.getDestinationIp())
                .sourcePort(request.getSourcePort())
                .destinationPort(request.getDestinationPort())
                .protocol(request.getProtocol())
                .recommendedAction(prediction.getRecommendedAction())
                .status(ThreatStatus.DETECTED)
                .detectedAt(LocalDateTime.now())
                .riskScore(BigDecimal.valueOf(prediction.getRiskScore() != null ? prediction.getRiskScore() : 0))
                .reasoning(prediction.getReasoning())
                .contributingFactors(serializeFactors(prediction.getContributingFactors()))
                .shapExplanation(serializeFactors(prediction.getShapExplanation()))
                .build();

        threat = threatRepository.save(threat);
        // Routes to either the RL-based autoRespondAdaptive() or the static
        // autoRespond() playbook depending on the adaptive-mode-enabled config
        // flag (see ResponseActionService.handleThreatResponse).
        Incident incident = responseActionService.handleThreatResponse(threat);
        sseHubService.broadcastDashboardUpdate("THREAT_DETECTED");

        auditLogService.log(actor, "SIMULATE_THREAT_DETECTION", "Threat", threat.getId(),
                String.format("Ran AI threat detection: %s (%s, %.1f%% confidence)",
                        threat.getThreatType(), threat.getSeverity(), threat.getConfidenceScore()),
                null);

        return ThreatDetectionResponse.builder()
                .threat(threat)
                .incidentCreated(incident != null)
                .incidentId(incident != null ? incident.getId() : null)
                .incidentNumber(incident != null ? incident.getIncidentNumber() : null)
                .build();
    }

    public Page<Threat> getThreats(Pageable pageable) {
        return threatRepository.findAll(pageable);
    }

    public Page<Threat> getThreatsBySeverity(Severity severity, Pageable pageable) {
        return threatRepository.findBySeverity(severity, pageable);
    }

    public Page<Threat> getThreatsByType(ThreatType type, Pageable pageable) {
        return threatRepository.findByThreatType(type, pageable);
    }

    public Threat getThreatOrThrow(Long id) {
        return threatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Threat not found with id: " + id));
    }

    public List<Threat> getRecentThreats() {
        return threatRepository.findTop10ByOrderByDetectedAtDesc();
    }

    @Transactional
    public Threat updateStatus(Long id, ThreatStatus status) {
        Threat threat = getThreatOrThrow(id);
        threat.setStatus(status);
        return threatRepository.save(threat);
    }

    public long countActiveThreats() {
        return threatRepository.countByStatus(ThreatStatus.DETECTED) + threatRepository.countByStatus(ThreatStatus.ANALYZING);
    }

    public long countBySeverity(Severity severity) {
        return threatRepository.countBySeverity(severity);
    }

    public long countSince(LocalDateTime since) {
        return threatRepository.countByDetectedAtAfter(since);
    }

    public List<Threat> findSince(LocalDateTime since) {
        return threatRepository.findByDetectedAtAfter(since);
    }

    private ThreatType parseThreatType(String value) {
        try {
            return ThreatType.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return ThreatType.UNKNOWN;
        }
    }

    private Severity parseSeverity(String value) {
        try {
            return Severity.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return Severity.LOW;
        }
    }

    private String serializeFactors(Object contributingFactors) {
        try {
            return objectMapper.writeValueAsString(contributingFactors);
        } catch (Exception e) {
            log.warn("Failed to serialize contributing factors: {}", e.getMessage());
            return "[]";
        }
    }
}
