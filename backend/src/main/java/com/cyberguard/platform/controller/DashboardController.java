package com.cyberguard.platform.controller;

import com.cyberguard.platform.dto.response.DashboardSummaryResponse;
import com.cyberguard.platform.dto.response.RiskScoreResponse;
import com.cyberguard.platform.dto.response.SecuritySummaryResponse;
import com.cyberguard.platform.service.DashboardService;
import com.cyberguard.platform.service.RiskScoreService;
import com.cyberguard.platform.service.SecuritySummaryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Aggregated security score, threat/incident statistics and trends")
public class DashboardController {

    private final DashboardService dashboardService;
    private final RiskScoreService riskScoreService;
    private final SecuritySummaryService securitySummaryService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    @GetMapping("/risk-score")
    public ResponseEntity<RiskScoreResponse> getRiskScore() {
        return ResponseEntity.ok(riskScoreService.computeRiskScore());
    }

    @GetMapping("/security-summary")
    public ResponseEntity<SecuritySummaryResponse> getSecuritySummary() {
        return ResponseEntity.ok(securitySummaryService.generateSummary());
    }
}
