package com.cyberguard.platform.service;

import com.cyberguard.platform.entity.*;
import com.cyberguard.platform.entity.enums.*;
import com.cyberguard.platform.exception.ResourceNotFoundException;
import com.cyberguard.platform.repository.BlockedIpRepository;
import com.cyberguard.platform.repository.ResponseActionRepository;
import com.cyberguard.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
public class ResponseActionService {

    private final ResponseActionRepository responseActionRepository;
    private final BlockedIpRepository blockedIpRepository;
    private final UserRepository userRepository;
    private final IncidentService incidentService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

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
