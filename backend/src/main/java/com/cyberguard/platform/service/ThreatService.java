package com.cyberguard.platform.service;

import com.cyberguard.platform.client.AiServiceClient;
import com.cyberguard.platform.dto.request.ThreatDetectionRequest;
import com.cyberguard.platform.dto.response.AiPredictionResponse;
import com.cyberguard.platform.dto.response.TemporalPredictionResponse;
import com.cyberguard.platform.dto.response.ThreatDetectionResponse;
import com.cyberguard.platform.entity.Incident;
import com.cyberguard.platform.entity.Threat;
import com.cyberguard.platform.entity.User;
import com.cyberguard.platform.entity.enums.CrossModelAgreement;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private static final Set<String> MODEL_A_BENIGN_LABELS = Set.of("BENIGN", "UNKNOWN");

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

        Severity baseSeverity = parseSeverity(prediction.getSeverity());
        CrossModelResult crossModel = runCrossModelCheck(request, prediction.getThreatType());
        Severity finalSeverity = baseSeverity;
        String reasoning = prediction.getReasoning();

        if (crossModel.agreement() == CrossModelAgreement.AGREE) {
            finalSeverity = escalate(baseSeverity);
            reasoning = appendNote(reasoning, String.format(
                    "Cross-checked with the attention-LSTM temporal detector (Model B, category: %s): both "
                            + "models agree this looks malicious - severity escalated from %s to %s.",
                    crossModel.temporalCategory(), baseSeverity, finalSeverity));
        } else if (crossModel.agreement() == CrossModelAgreement.DISAGREE) {
            reasoning = appendNote(reasoning, String.format(
                    "Cross-checked with the attention-LSTM temporal detector (Model B, category: %s): the two "
                            + "models disagree - flagged for analyst review, severity left unchanged.",
                    crossModel.temporalCategory()));
        }

        Threat threat = Threat.builder()
                .threatType(parseThreatType(prediction.getThreatType()))
                .severity(finalSeverity)
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
                .reasoning(reasoning)
                .contributingFactors(serializeFactors(prediction.getContributingFactors()))
                .shapExplanation(serializeFactors(prediction.getShapExplanation()))
                .temporalCategory(crossModel.temporalCategory())
                .crossModelAgreement(crossModel.agreement())
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

    private record CrossModelResult(String temporalCategory, CrossModelAgreement agreement) {}

    /**
     * Runs Model B (attention-LSTM temporal detector) on the same event Model A just
     * classified, and compares the two on malicious-vs-benign. Best-effort: Model B
     * may be unavailable in some deployments (see AiServiceClient.predictTemporal()'s
     * javadoc), in which case this returns UNAVAILABLE rather than failing the whole
     * detection - Model A's classification and the rest of detectAndPersist() must
     * not depend on Model B being reachable.
     */
    private CrossModelResult runCrossModelCheck(ThreatDetectionRequest request, String modelAThreatType) {
        try {
            TemporalPredictionResponse temporal = aiServiceClient.predictTemporal(List.of(deriveKddRecord(request)));
            boolean modelAMalicious = modelAThreatType == null
                    || !MODEL_A_BENIGN_LABELS.contains(modelAThreatType.toUpperCase());
            boolean modelBMalicious = temporal.getThreatCategory() != null
                    && !"NORMAL".equalsIgnoreCase(temporal.getThreatCategory());
            CrossModelAgreement agreement = modelAMalicious == modelBMalicious
                    ? CrossModelAgreement.AGREE : CrossModelAgreement.DISAGREE;
            return new CrossModelResult(temporal.getThreatCategory(), agreement);
        } catch (Exception ex) {
            log.warn("Cross-model check with temporal detector (Model B) skipped: {}", ex.getMessage());
            return new CrossModelResult(null, CrossModelAgreement.UNAVAILABLE);
        }
    }

    /**
     * Model B (attention-LSTM) expects NSL-KDD-shaped connection-record features -
     * a different schema from Model A's request fields (see
     * ai-service/app/schemas/temporal.py's module docstring). The live detection
     * pipeline only has Model A's fields available, so this is a deliberately
     * approximate, best-effort translation for the cross-model agreement check
     * above - NOT a claim that this reconstructs a genuine NSL-KDD record. Any
     * field not set here falls back to KddConnectionRecord's own default.
     */
    private Map<String, Object> deriveKddRecord(ThreatDetectionRequest request) {
        Map<String, Object> kddRecord = new HashMap<>();
        if (request.getProtocol() != null) {
            kddRecord.put("protocol_type", request.getProtocol().toLowerCase());
        }
        if (request.getDurationMs() != null) {
            kddRecord.put("duration", request.getDurationMs() / 1000.0);
        }
        if (request.getBytesTransferred() != null) {
            kddRecord.put("src_bytes", request.getBytesTransferred());
        }
        if (request.getPacketCount() != null) {
            kddRecord.put("count", request.getPacketCount());
        }
        if (request.getFailedLogins() != null) {
            kddRecord.put("num_failed_logins", request.getFailedLogins());
        }
        return kddRecord;
    }

    private Severity escalate(Severity severity) {
        return switch (severity) {
            case LOW -> Severity.MEDIUM;
            case MEDIUM -> Severity.HIGH;
            case HIGH, CRITICAL -> Severity.CRITICAL;
        };
    }

    private String appendNote(String base, String note) {
        return (base == null || base.isBlank()) ? note : base + " " + note;
    }
}
