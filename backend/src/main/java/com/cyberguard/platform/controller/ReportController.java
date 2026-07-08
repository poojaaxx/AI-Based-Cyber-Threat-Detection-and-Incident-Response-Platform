package com.cyberguard.platform.controller;

import com.cyberguard.platform.security.CustomUserDetails;
import com.cyberguard.platform.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
@Tag(name = "Reports", description = "PDF, CSV and Excel report generation for threats, incidents, audit logs, and enterprise risk")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/threats/csv")
    public ResponseEntity<byte[]> threatsCsv() throws IOException {
        return fileResponse(reportService.generateThreatsCsv(), "threats-report.csv", "text/csv");
    }

    @GetMapping("/threats/excel")
    public ResponseEntity<byte[]> threatsExcel() throws IOException {
        return fileResponse(reportService.generateThreatsExcel(), "threats-report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/threats/pdf")
    public ResponseEntity<byte[]> threatsPdf(@AuthenticationPrincipal CustomUserDetails principal) throws IOException {
        return fileResponse(reportService.generateThreatsPdf(principal.getUsername()), "threats-report.pdf", "application/pdf");
    }

    @GetMapping("/incidents/csv")
    public ResponseEntity<byte[]> incidentsCsv() throws IOException {
        return fileResponse(reportService.generateIncidentsCsv(), "incidents-report.csv", "text/csv");
    }

    @GetMapping("/incidents/excel")
    public ResponseEntity<byte[]> incidentsExcel() throws IOException {
        return fileResponse(reportService.generateIncidentsExcel(), "incidents-report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/incidents/pdf")
    public ResponseEntity<byte[]> incidentsPdf(@AuthenticationPrincipal CustomUserDetails principal) throws IOException {
        return fileResponse(reportService.generateIncidentsPdf(principal.getUsername()), "incidents-report.pdf", "application/pdf");
    }

    @GetMapping("/audit-logs/csv")
    public ResponseEntity<byte[]> auditLogsCsv() throws IOException {
        return fileResponse(reportService.generateAuditLogsCsv(), "audit-logs-report.csv", "text/csv");
    }

    @GetMapping("/audit-logs/excel")
    public ResponseEntity<byte[]> auditLogsExcel() throws IOException {
        return fileResponse(reportService.generateAuditLogsExcel(), "audit-logs-report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/simulations/csv")
    public ResponseEntity<byte[]> simulationsCsv() throws IOException {
        return fileResponse(reportService.generateSimulationsCsv(), "simulation-history-report.csv", "text/csv");
    }

    @GetMapping("/risk-summary/pdf")
    public ResponseEntity<byte[]> riskSummaryPdf(@AuthenticationPrincipal CustomUserDetails principal) throws IOException {
        return fileResponse(reportService.generateRiskSummaryPdf(principal.getUsername()), "enterprise-risk-summary.pdf", "application/pdf");
    }

    private ResponseEntity<byte[]> fileResponse(byte[] content, String filename, String contentType) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }
}
