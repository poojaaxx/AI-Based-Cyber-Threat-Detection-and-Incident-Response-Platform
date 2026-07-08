package com.cyberguard.platform.controller;

import com.cyberguard.platform.dto.request.AddCommentRequest;
import com.cyberguard.platform.dto.request.CreateIncidentRequest;
import com.cyberguard.platform.dto.request.UpdateIncidentRequest;
import com.cyberguard.platform.dto.response.AssignableUserResponse;
import com.cyberguard.platform.entity.Incident;
import com.cyberguard.platform.entity.IncidentComment;
import com.cyberguard.platform.entity.IncidentTimeline;
import com.cyberguard.platform.entity.enums.IncidentStatus;
import com.cyberguard.platform.security.CustomUserDetails;
import com.cyberguard.platform.service.IncidentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
@Tag(name = "Incidents", description = "Incident management: create, assign, update, resolve, comment")
public class IncidentController {

    private final IncidentService incidentService;

    @PostMapping
    public ResponseEntity<Incident> createIncident(@Valid @RequestBody CreateIncidentRequest request,
                                                     @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(incidentService.createIncident(request, principal.getUser()));
    }

    @GetMapping
    public ResponseEntity<Page<Incident>> getIncidents(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) IncidentStatus status) {
        if (status != null) {
            return ResponseEntity.ok(incidentService.getIncidentsByStatus(status, pageable));
        }
        return ResponseEntity.ok(incidentService.getIncidents(pageable));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Incident>> getRecentIncidents() {
        return ResponseEntity.ok(incidentService.getRecentIncidents());
    }

    /** Active Admins/Analysts available for assignment - backs the Incident Detail "Assign Analyst" dropdown. */
    @GetMapping("/assignable-users")
    public ResponseEntity<List<AssignableUserResponse>> getAssignableUsers() {
        return ResponseEntity.ok(incidentService.getAssignableUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Incident> getIncident(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getIncidentOrThrow(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Incident> updateIncident(@PathVariable Long id, @Valid @RequestBody UpdateIncidentRequest request,
                                                     @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(incidentService.updateIncident(id, request, principal.getUser()));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<IncidentComment> addComment(@PathVariable Long id, @Valid @RequestBody AddCommentRequest request,
                                                        @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(incidentService.addComment(id, request, principal.getUser()));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<IncidentComment>> getComments(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getComments(id));
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<IncidentTimeline>> getTimeline(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getTimeline(id));
    }
}
