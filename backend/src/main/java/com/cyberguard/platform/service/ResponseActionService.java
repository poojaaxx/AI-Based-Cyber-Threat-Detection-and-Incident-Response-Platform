package com.cyberguard.platform.service;

import com.cyberguard.platform.client.AiServiceClient;
import com.cyberguard.platform.dto.response.PolicyRecommendationResponse;
import com.cyberguard.platform.entity.*;
import com.cyberguard.platform.entity.enums.*;
import com.cyberguard.platform.exception.ResourceNotFoundException;
import com.cyberguard.platform.repository.BlockedIpRepository;
import com.cyberguard.platform.repository.ResponseActionRepository;
import com.cyberguard.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the Automated Incident Response module: block IP, disable user,
 * quarantine threat, generate incident, notify administrator. Every action is
 * persisted to response_actions and mirrored into the audit log.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResponseActionService {

    private final ResponseActionRepository responseActionRepository;
    private final BlockedIpRepository blockedIpRepository;
    private final UserRepository userRepository;
    private final IncidentService incidentService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final AiServiceClient aiServiceClient;

    /** Config flag toggling whether newly detected threats are routed through
     * the RL-based autoRespondAdaptive() or the static autoRespond() playbook.
     * Seeded from application.yml: app.response.adaptive-mode-enabled at
     * startup, but mutable at runtime via the getter/setter below - so the
     * Settings page can flip it live without an env var + restart. Resets to
     * the config-file default on the next restart (not persisted to a DB). */
    @Value("${app.response.adaptive-mode-enabled:false}")
    private boolean adaptiveModeEnabled;

    public boolean isAdaptiveModeEnabled() {
        return adaptiveModeEnabled;
    }

    public void setAdaptiveModeEnabled(boolean adaptiveModeEnabled) {
        this.adaptiveModeEnabled = adaptiveModeEnabled;
    }

    /** Single entry point used by the detection pipeline (ThreatService) -
     * routes to the adaptive RL policy or the static playbook depending on
     * the adaptive-mode-enabled config flag. Both underlying methods remain
     * independently callable/unchanged. */
    @Transactional
    public Incident handleThreatResponse(Threat threat) {
        return adaptiveModeEnabled ? autoRespondAdaptive(threat) : autoRespond(threat);
    }

    /** Runs the automated playbook for a newly detected threat. */
    @Transactional
    public Incident autoRespond(Threat threat) {
        Incident incident = null;

        if (threat.getSeverity() == Severity.CRITICAL || threat.getSeverity() == Severity.HIGH) {
            if (threat.getSourceIp() != null) {
                blockIp(threat.getSourceIp(), "Automated block: " + threat.getThreatType() + " detected", null, threat, null);
            }

            if (threat.getThreatType() == ThreatType.INSIDER_THREAT && threat.getAffectedUser() != null) {
                disableUser(threat.getAffectedUser().getId(), "Automated disable: insider threat detected", threat, null);
            }

            quarantineThreat(threat, null);
            incident = incidentService.createAutomatedIncident(threat);
            recordAction(ActionType.GENERATE_INCIDENT, incident.getIncidentNumber(), incident, threat, null,
                    "Automatically generated incident " + incident.getIncidentNumber());
        }

        notificationService.notifyAllAdmins(
                threat.getSeverity() == Severity.CRITICAL ? "Critical Risk Detected" : "New Threat Detected",
                threat.getThreatType() + " from " + threat.getSourceIp() + " | Confidence: " + threat.getConfidenceScore() + "%",
                threat.getSeverity() == Severity.CRITICAL ? NotificationType.CRITICAL : NotificationType.THREAT,
                threat.getSeverity(),
                threat.getSeverity() == Severity.CRITICAL ? "critical" : "threat"
        );
        recordAction(ActionType.NOTIFY_ADMIN, "ALL_ADMINS", incident, threat, null, "Notified administrators of new threat");

        return incident;
    }

    /**
     * RL-inspired adaptive response: asks the AI service's tabular Q-learning
     * policy (app/ml/response_policy.py) which action best fits this threat's
     * (type, severity, confidence), then executes it. Falls back to the
     * static autoRespond() playbook if the AI service call fails for any
     * reason, so a downstream outage never blocks incident response.
     */
    @Transactional
    public Incident autoRespondAdaptive(Threat threat) {
        try {
            double confidence = threat.getConfidenceScore() != null
                    ? threat.getConfidenceScore().doubleValue() / 100.0 : 0.0;
            PolicyRecommendationResponse recommendation = aiServiceClient.recommendAction(
                    threat.getThreatType().name(), threat.getSeverity().name(), confidence);
            if (recommendation == null || recommendation.getRecommendedAction() == null) {
                throw new IllegalStateException("AI service returned an empty policy recommendation");
            }
            return executeRecommendedAction(threat, recommendation);
        } catch (Exception ex) {
            log.warn("Adaptive response policy unavailable, falling back to static autoRespond(): {}", ex.getMessage());
            return autoRespond(threat);
        }
    }

    private Incident executeRecommendedAction(Threat threat, PolicyRecommendationResponse recommendation) {
        String action = recommendation.getRecommendedAction();
        String reasonPrefix = "Adaptive policy (state=" + recommendation.getState() + ") recommended " + action + ": ";
        Incident incident = null;

        switch (action) {
            case "BLOCK_IP":
                if (threat.getSourceIp() != null) {
                    blockIp(threat.getSourceIp(), reasonPrefix + "blocking source IP", null, threat, null);
                }
                quarantineThreat(threat, null);
                break;
            case "DISABLE_USER":
                if (threat.getAffectedUser() != null) {
                    disableUser(threat.getAffectedUser().getId(), reasonPrefix + "disabling affected user", threat, null);
                } else if (threat.getSourceIp() != null) {
                    blockIp(threat.getSourceIp(), reasonPrefix + "no affected user on record, blocking source IP instead", null, threat, null);
                }
                quarantineThreat(threat, null);
                break;
            case "QUARANTINE":
                quarantineThreat(threat, null);
                break;
            case "ESCALATE":
                quarantineThreat(threat, null);
                incident = incidentService.createAutomatedIncident(threat);
                recordAction(ActionType.GENERATE_INCIDENT, incident.getIncidentNumber(), incident, threat, null,
                        reasonPrefix + "escalated to incident " + incident.getIncidentNumber());
                break;
            case "NOTIFY_ONLY":
            default:
                break;
        }

        notificationService.notifyAllAdmins(
                "ESCALATE".equals(action) ? "Critical Risk Detected" : "Adaptive Response: " + action,
                threat.getThreatType() + " from " + threat.getSourceIp() + " | Confidence: " + threat.getConfidenceScore() + "%",
                "ESCALATE".equals(action) ? NotificationType.CRITICAL : NotificationType.THREAT,
                threat.getSeverity(),
                "ESCALATE".equals(action) ? "critical" : "threat"
        );
        recordAction(ActionType.NOTIFY_ADMIN, "ALL_ADMINS", incident, threat, null,
                reasonPrefix + "notified administrators");

        return incident;
    }

    @Transactional
    public ResponseAction blockIp(String ipAddress, String reason, Incident incident, Threat threat, User performedBy) {
        blockedIpRepository.findByIpAddress(ipAddress).ifPresentOrElse(existing -> {
            existing.setActive(true);
            existing.setReason(reason);
            blockedIpRepository.save(existing);
        }, () -> blockedIpRepository.save(BlockedIp.builder()
                .ipAddress(ipAddress).reason(reason).blockedBy(performedBy).active(true).build()));

        return recordAction(ActionType.BLOCK_IP, ipAddress, incident, threat, performedBy, reason);
    }

    @Transactional
    public ResponseAction disableUser(Long userId, String reason, Threat threat, User performedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);

        return recordAction(ActionType.DISABLE_USER, user.getUsername(), null, threat, performedBy, reason);
    }

    @Transactional
    public ResponseAction quarantineThreat(Threat threat, User performedBy) {
        threat.setStatus(ThreatStatus.MITIGATED);
        return recordAction(ActionType.QUARANTINE_THREAT, "Threat#" + threat.getId(), null, threat, performedBy,
                "Threat quarantined and marked as mitigated");
    }

    public Page<ResponseAction> getActions(Pageable pageable) {
        return responseActionRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private ResponseAction recordAction(ActionType type, String target, Incident incident, Threat threat,
                                         User performedBy, String details) {
        ResponseAction action = ResponseAction.builder()
                .incident(incident).threat(threat).actionType(type).target(target)
                .status(ActionStatus.SUCCESS)
                .triggeredBy(performedBy == null ? TriggerSource.AUTOMATED : TriggerSource.MANUAL)
                .performedBy(performedBy)
                .details(details)
                .build();
        action = responseActionRepository.save(action);
        auditLogService.log(performedBy, "RESPONSE_ACTION_" + type, "ResponseAction", action.getId(), details, null);
        return action;
    }
}
