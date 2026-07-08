package com.cyberguard.platform.service;

import com.cyberguard.platform.dto.request.AddCommentRequest;
import com.cyberguard.platform.dto.request.CreateIncidentRequest;
import com.cyberguard.platform.dto.request.UpdateIncidentRequest;
import com.cyberguard.platform.dto.response.AssignableUserResponse;
import com.cyberguard.platform.entity.*;
import com.cyberguard.platform.entity.enums.IncidentStatus;
import com.cyberguard.platform.entity.enums.NotificationType;
import com.cyberguard.platform.entity.enums.Severity;
import com.cyberguard.platform.entity.enums.UserStatus;
import com.cyberguard.platform.exception.ResourceNotFoundException;
import com.cyberguard.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentCommentRepository incidentCommentRepository;
    private final IncidentTimelineRepository incidentTimelineRepository;
    private final ThreatRepository threatRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional
    public Incident createIncident(CreateIncidentRequest request, User reporter) {
        Threat threat = null;
        if (request.getThreatId() != null) {
            threat = threatRepository.findById(request.getThreatId())
                    .orElseThrow(() -> new ResourceNotFoundException("Threat not found"));
        }

        User assignee = null;
        if (request.getAssignedTo() != null) {
            assignee = userRepository.findById(request.getAssignedTo())
                    .orElseThrow(() -> new ResourceNotFoundException("Assignee not found"));
        }

        Incident incident = Incident.builder()
                .incidentNumber(generateIncidentNumber())
                .title(request.getTitle())
                .description(request.getDescription())
                .severity(Severity.valueOf(request.getSeverity().toUpperCase()))
                .status(assignee != null ? IncidentStatus.ASSIGNED : IncidentStatus.OPEN)
                .threat(threat)
                .reportedBy(reporter)
                .assignedTo(assignee)
                .build();

        incident = incidentRepository.save(incident);
        addTimelineEvent(incident, "CREATED", "Incident created by " + reporter.getUsername(), reporter);

        if (assignee != null) {
            addTimelineEvent(incident, "ASSIGNED", "Assigned to " + assignee.getUsername(), reporter);
            notificationService.notifyUser(assignee, "New Incident Assigned",
                    "You have been assigned incident " + incident.getIncidentNumber() + ": " + incident.getTitle(),
                    NotificationType.INCIDENT);
        }

        notificationService.notifyAllAdmins("Incident Created",
                incident.getIncidentNumber() + ": " + incident.getTitle(),
                NotificationType.INCIDENT, incident.getSeverity(), "incident-created");

        auditLogService.log(reporter, "CREATE_INCIDENT", "Incident", incident.getId(),
                "Created incident " + incident.getIncidentNumber(), null);

        return incident;
    }

    public Incident createAutomatedIncident(Threat threat) {
        Incident incident = Incident.builder()
                .incidentNumber(generateIncidentNumber())
                .title(threat.getThreatType() + " detected from " + threat.getSourceIp())
                .description("Automatically generated incident from AI threat detection engine. " +
                        "Recommended action: " + threat.getRecommendedAction())
                .severity(threat.getSeverity())
                .status(IncidentStatus.OPEN)
                .threat(threat)
                .reportedBy(systemUser())
                .build();
        incident = incidentRepository.save(incident);
        addTimelineEvent(incident, "CREATED", "Auto-generated from AI threat detection", null);
        return incident;
    }

    @Transactional
    public Incident updateIncident(Long id, UpdateIncidentRequest request, User actor) {
        Incident incident = getIncidentOrThrow(id);

        if (request.getStatus() != null) {
            IncidentStatus newStatus = IncidentStatus.valueOf(request.getStatus().toUpperCase());
            incident.setStatus(newStatus);
            if (newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.CLOSED) {
                incident.setResolvedAt(LocalDateTime.now());
                notificationService.notifyAllAdmins("Incident Closed",
                        incident.getIncidentNumber() + " marked " + newStatus,
                        NotificationType.INCIDENT, Severity.LOW, "incident-closed");
            }
            addTimelineEvent(incident, "STATUS_CHANGED", "Status changed to " + newStatus, actor);
        }

        if (request.getAssignedTo() != null) {
            User assignee = userRepository.findById(request.getAssignedTo())
                    .orElseThrow(() -> new ResourceNotFoundException("Assignee not found"));
            incident.setAssignedTo(assignee);
            addTimelineEvent(incident, "ASSIGNED", "Assigned to " + assignee.getUsername(), actor);
            notificationService.notifyUser(assignee, "Incident Assigned",
                    "You have been assigned incident " + incident.getIncidentNumber(), NotificationType.INCIDENT);
        }

        if (request.getResolutionNotes() != null) {
            incident.setResolutionNotes(request.getResolutionNotes());
        }

        incident = incidentRepository.save(incident);
        auditLogService.log(actor, "UPDATE_INCIDENT", "Incident", incident.getId(),
                "Updated incident " + incident.getIncidentNumber(), null);
        return incident;
    }

    public IncidentComment addComment(Long incidentId, AddCommentRequest request, User user) {
        Incident incident = getIncidentOrThrow(incidentId);
        IncidentComment comment = IncidentComment.builder()
                .incident(incident).user(user).comment(request.getComment()).build();
        comment = incidentCommentRepository.save(comment);
        addTimelineEvent(incident, "COMMENTED", user.getUsername() + " added a comment", user);
        return comment;
    }

    public List<IncidentComment> getComments(Long incidentId) {
        return incidentCommentRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
    }

    public List<IncidentTimeline> getTimeline(Long incidentId) {
        return incidentTimelineRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
    }

    public Page<Incident> getIncidents(Pageable pageable) {
        return incidentRepository.findAll(pageable);
    }

    public Page<Incident> getIncidentsByStatus(IncidentStatus status, Pageable pageable) {
        return incidentRepository.findByStatus(status, pageable);
    }

    public Incident getIncidentOrThrow(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));
    }

    public List<Incident> getRecentIncidents() {
        return incidentRepository.findTop10ByOrderByCreatedAtDesc();
    }

    /** Users an incident can be assigned to - active Admins/Analysts, for the Assign Analyst dropdown. */
    public List<AssignableUserResponse> getAssignableUsers() {
        return userRepository.findByRoles_NameInAndStatus(List.of(Role.ADMIN, Role.ANALYST), UserStatus.ACTIVE)
                .stream()
                .map(u -> AssignableUserResponse.builder().id(u.getId()).username(u.getUsername()).fullName(u.getFullName()).build())
                .collect(Collectors.toList());
    }

    private void addTimelineEvent(Incident incident, String eventType, String description, User actor) {
        incidentTimelineRepository.save(IncidentTimeline.builder()
                .incident(incident).eventType(eventType).description(description).performedBy(actor).build());
    }

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100000);

    private String generateIncidentNumber() {
        int year = LocalDateTime.now().getYear();
        long seq = SEQUENCE.incrementAndGet();
        return String.format("INC-%d-%06d", year, seq);
    }

    private User systemUser() {
        return userRepository.findByUsername("admin").orElse(null);
    }
}
