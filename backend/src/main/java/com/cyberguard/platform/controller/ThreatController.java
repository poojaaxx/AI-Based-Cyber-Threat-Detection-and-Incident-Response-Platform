package com.cyberguard.platform.controller;

import com.cyberguard.platform.dto.request.TemporalPredictionRequest;
import com.cyberguard.platform.dto.request.ThreatDetectionRequest;
import com.cyberguard.platform.dto.response.ThreatDetectionResponse;
import com.cyberguard.platform.dto.response.ThreatExplanationResponse;
import com.cyberguard.platform.dto.response.ThreatInvestigationResponse;
import com.cyberguard.platform.client.AiServiceClient;
import com.cyberguard.platform.entity.Threat;
import com.cyberguard.platform.entity.enums.Severity;
import com.cyberguard.platform.entity.enums.ThreatStatus;
import com.cyberguard.platform.entity.enums.ThreatType;
import com.cyberguard.platform.security.CustomUserDetails;
import com.cyberguard.platform.service.ThreatInvestigationService;
import com.cyberguard.platform.service.ThreatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/threats")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Threats", description = "AI-based threat detection results")
public class ThreatController {

    private final ThreatService threatService;
    private final ThreatInvestigationService threatInvestigationService;
    private final AiServiceClient aiServiceClient;

    @PostMapping("/detect")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public ResponseEntity<ThreatDetectionResponse> detect(@RequestBody ThreatDetectionRequest request,
                                                            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(threatService.detectAndPersist(request, principal.getUser()));
    }

    @GetMapping
    public ResponseEntity<Page<Threat>> getThreats(
            @PageableDefault(size = 20, sort = "detectedAt") Pageable pageable,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) ThreatType type) {

        if (severity != null) {
            return ResponseEntity.ok(threatService.getThreatsBySeverity(severity, pageable));
        }
        if (type != null) {
            return ResponseEntity.ok(threatService.getThreatsByType(type, pageable));
        }
        return ResponseEntity.ok(threatService.getThreats(pageable));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Threat>> getRecentThreats() {
        return ResponseEntity.ok(threatService.getRecentThreats());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Threat> getThreat(@PathVariable Long id) {
        return ResponseEntity.ok(threatService.getThreatOrThrow(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
    public ResponseEntity<Threat> updateStatus(@PathVariable Long id, @RequestParam ThreatStatus status) {
        return ResponseEntity.ok(threatService.updateStatus(id, status));
    }

    @GetMapping("/{id}/explain")
    public ResponseEntity<ThreatExplanationResponse> explain(@PathVariable Long id) {
        return ResponseEntity.ok(threatInvestigationService.explain(id));
    }

    @GetMapping("/{id}/investigation")
    public ResponseEntity<ThreatInvestigationResponse> investigate(@PathVariable Long id) {
        return ResponseEntity.ok(threatInvestigationService.investigate(id));
    }

    /**
     * Demo/comparison endpoint for the attention-LSTM temporal detector
     * ("Model B", trained on NSL-KDD) - proxies to the AI service's
     * POST /api/v1/predict/temporal. Independent of detect(): it doesn't
     * persist a Threat or trigger incident response, it just returns the
     * model's classification so the UI can show it side-by-side with the
     * primary RandomForest detector.
     */
    @PostMapping("/predict-temporal")
    public ResponseEntity<?> predictTemporal(@RequestBody TemporalPredictionRequest request) {
        try {
            return ResponseEntity.ok(aiServiceClient.predictTemporal(request.getRecords()));
        } catch (Exception ex) {
            log.warn("Temporal detector (Model B) unavailable: {}", ex.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "message", "The attention-LSTM temporal detector (Model B) is currently unavailable."));
        }
    }
}
