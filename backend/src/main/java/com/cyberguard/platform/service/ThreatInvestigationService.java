package com.cyberguard.platform.service;

import com.cyberguard.platform.dto.response.*;
import com.cyberguard.platform.entity.Incident;
import com.cyberguard.platform.entity.IncidentTimeline;
import com.cyberguard.platform.entity.ResponseAction;
import com.cyberguard.platform.entity.Threat;
import com.cyberguard.platform.entity.enums.ActionType;
import com.cyberguard.platform.repository.IncidentRepository;
import com.cyberguard.platform.repository.IncidentTimelineRepository;
import com.cyberguard.platform.repository.ResponseActionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the "Explain AI" panel and the full "Attack Investigation" timeline
 * for a threat, stitching together data already captured elsewhere (the AI
 * service's explainability output persisted on Threat, MITRE ATT&CK mapping,
 * automated response actions, and - if one was auto-generated - the linked
 * incident's own timeline) into two purpose-built read views.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThreatInvestigationService {

    private final ThreatService threatService;
    private final ResponseActionRepository responseActionRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentTimelineRepository incidentTimelineRepository;
    private final MitreMappingService mitreMappingService;
    private final AiRecommendationService aiRecommendationService;
    private final ObjectMapper objectMapper;

    public ThreatExplanationResponse explain(Long threatId) {
        Threat threat = threatService.getThreatOrThrow(threatId);
        return buildExplanation(threat);
    }

    @Transactional(readOnly = true)
    public ThreatInvestigationResponse investigate(Long threatId) {
        Threat threat = threatService.getThreatOrThrow(threatId);
        ThreatExplanationResponse explanation = buildExplanation(threat);

        List<ResponseAction> actions = responseActionRepository.findByThreatIdOrderByCreatedAtAsc(threatId);
        Incident incident = incidentRepository.findByThreatId(threatId).orElse(null);

        List<InvestigationEvent> timeline = new ArrayList<>();
        timeline.add(InvestigationEvent.builder()
                .timestamp(threat.getDetectedAt())
                .eventType("DETECTED")
                .title(threat.getThreatType() + " detected")
                .description(String.format("AI classified this event as %s (%s severity, %.1f%% confidence) from %s.",
                        threat.getThreatType(), threat.getSeverity(), threat.getConfidenceScore(), threat.getSourceIp()))
                .actor("AI Threat Detection Engine")
                .build());

        for (ResponseAction action : actions) {
            timeline.add(InvestigationEvent.builder()
                    .timestamp(action.getCreatedAt())
                    .eventType("RESPONSE_ACTION_" + action.getActionType())
                    .title(humanizeActionType(action.getActionType().name()))
                    .description(action.getDetails())
                    .actor(action.getPerformedBy() != null ? action.getPerformedBy().getUsername() : "Automated Response Engine")
                    .build());
        }

        IncidentSummary incidentSummary = null;
        if (incident != null) {
            incidentSummary = IncidentSummary.builder()
                    .id(incident.getId())
                    .incidentNumber(incident.getIncidentNumber())
                    .title(incident.getTitle())
                    .severity(incident.getSeverity().name())
                    .status(incident.getStatus().name())
                    .assignedToUsername(incident.getAssignedTo() != null ? incident.getAssignedTo().getUsername() : null)
                    .createdAt(incident.getCreatedAt())
                    .resolvedAt(incident.getResolvedAt())
                    .build();

            boolean hasAutoGenerateAction = actions.stream().anyMatch(a -> a.getActionType() == ActionType.GENERATE_INCIDENT);
            List<IncidentTimeline> incidentEvents = incidentTimelineRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId());
            for (IncidentTimeline event : incidentEvents) {
                // The "CREATED" incident-timeline event duplicates the GENERATE_INCIDENT response
                // action above for automated incidents; skip it to avoid a redundant entry.
                if ("CREATED".equals(event.getEventType()) && hasAutoGenerateAction) {
                    continue;
                }
                timeline.add(InvestigationEvent.builder()
                        .timestamp(event.getCreatedAt())
                        .eventType("INCIDENT_" + event.getEventType())
                        .title(humanizeActionType(event.getEventType()))
                        .description(event.getDescription())
                        .actor(event.getPerformedBy() != null ? event.getPerformedBy().getUsername() : "System")
                        .build());
            }
        }

        timeline.sort(Comparator.comparing(InvestigationEvent::getTimestamp));

        return ThreatInvestigationResponse.builder()
                .threat(threat)
                .explanation(explanation)
                .incident(incidentSummary)
                .timeline(timeline)
                .build();
    }

    private ThreatExplanationResponse buildExplanation(Threat threat) {
        List<ContributingFactor> factors = deserializeFactors(threat.getContributingFactors());
        List<MitreTechniqueInfo> mitreTechniques = mitreMappingService.getTechniquesForThreatType(threat.getThreatType())
                .stream()
                .map(t -> MitreTechniqueInfo.builder()
                        .techniqueId(t.techniqueId())
                        .name(t.name())
                        .tactic(t.tactic())
                        .description(t.description())
                        .mitigation(t.mitigation())
                        .build())
                .toList();

        return ThreatExplanationResponse.builder()
                .threatId(threat.getId())
                .threatType(threat.getThreatType().name())
                .severity(threat.getSeverity().name())
                .confidenceScore(threat.getConfidenceScore())
                .riskScore(threat.getRiskScore())
                .reasoning(threat.getReasoning())
                .recommendedAction(threat.getRecommendedAction())
                .contributingFactors(factors)
                .mitreTechniques(mitreTechniques)
                .recommendations(aiRecommendationService.getRecommendations(threat.getThreatType(), threat.getSeverity()))
                .build();
    }

    private List<ContributingFactor> deserializeFactors(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ContributingFactor>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to deserialize contributing factors: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String humanizeActionType(String raw) {
        String[] words = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
}
