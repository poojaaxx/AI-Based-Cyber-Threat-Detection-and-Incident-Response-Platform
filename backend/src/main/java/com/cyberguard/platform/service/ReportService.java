package com.cyberguard.platform.service;

import com.cyberguard.platform.dto.response.RiskScoreResponse;
import com.cyberguard.platform.dto.response.SecuritySummaryResponse;
import com.cyberguard.platform.entity.AuditLog;
import com.cyberguard.platform.entity.Incident;
import com.cyberguard.platform.entity.SimulationRun;
import com.cyberguard.platform.entity.Threat;
import com.cyberguard.platform.repository.AuditLogRepository;
import com.cyberguard.platform.repository.IncidentRepository;
import com.cyberguard.platform.repository.SimulationRunRepository;
import com.cyberguard.platform.repository.ThreatRepository;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ThreatRepository threatRepository;
    private final IncidentRepository incidentRepository;
    private final SimulationRunRepository simulationRunRepository;
    private final AuditLogRepository auditLogRepository;
    private final RiskScoreService riskScoreService;
    private final SecuritySummaryService securitySummaryService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DeviceRgb BRAND_COLOR = new DeviceRgb(0, 229, 199); // matches the frontend's cg-accent teal

    // ---------------------------------------------------------------- Threats

    public byte[] generateThreatsCsv() throws IOException {
        List<Threat> threats = threatRepository.findAll();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8), CSVFormat.DEFAULT
                .builder().setHeader("ID", "Type", "Severity", "Confidence %", "Source IP", "Destination IP",
                        "Status", "Detected At").build())) {
            for (Threat t : threats) {
                printer.printRecord(t.getId(), t.getThreatType(), t.getSeverity(), t.getConfidenceScore(),
                        t.getSourceIp(), t.getDestinationIp(), t.getStatus(), t.getDetectedAt().format(FMT));
            }
        }
        return out.toByteArray();
    }

    public byte[] generateThreatsExcel() throws IOException {
        List<Threat> threats = threatRepository.findAll();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Threats");
            String[] cols = {"ID", "Type", "Severity", "Confidence %", "Source IP", "Destination IP", "Status", "Detected At"};
            writeHeaderRow(sheet, cols);

            int rowIdx = 1;
            for (Threat t : threats) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(t.getId());
                row.createCell(1).setCellValue(t.getThreatType().name());
                row.createCell(2).setCellValue(t.getSeverity().name());
                row.createCell(3).setCellValue(t.getConfidenceScore().doubleValue());
                row.createCell(4).setCellValue(t.getSourceIp());
                row.createCell(5).setCellValue(t.getDestinationIp());
                row.createCell(6).setCellValue(t.getStatus().name());
                row.createCell(7).setCellValue(t.getDetectedAt().format(FMT));
            }
            for (int c = 0; c < cols.length; c++) sheet.autoSizeColumn(c);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateThreatsPdf(String generatedBy) throws IOException {
        List<Threat> threats = threatRepository.findAll();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(out);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            addBrandedHeader(document, "Threat Report", generatedBy);
            document.add(new Paragraph("Total threats: " + threats.size()).setFontSize(10).setItalic());
            document.add(new Paragraph("\n"));

            Table table = new Table(UnitValue.createPercentArray(new float[]{10, 16, 12, 12, 18, 18, 14}))
                    .useAllAvailableWidth();
            for (String h : new String[]{"ID", "Type", "Severity", "Confidence", "Source IP", "Destination IP", "Detected At"}) {
                table.addHeaderCell(headerCell(h));
            }
            for (Threat t : threats) {
                table.addCell(String.valueOf(t.getId()));
                table.addCell(t.getThreatType().name());
                table.addCell(t.getSeverity().name());
                table.addCell(t.getConfidenceScore() + "%");
                table.addCell(t.getSourceIp() != null ? t.getSourceIp() : "-");
                table.addCell(t.getDestinationIp() != null ? t.getDestinationIp() : "-");
                table.addCell(t.getDetectedAt().format(FMT));
            }
            document.add(table);
        }
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- Incidents

    public byte[] generateIncidentsCsv() throws IOException {
        List<Incident> incidents = incidentRepository.findAll();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8), CSVFormat.DEFAULT
                .builder().setHeader("Incident #", "Title", "Severity", "Status", "Reported By", "Assigned To", "Created At").build())) {
            for (Incident i : incidents) {
                printer.printRecord(i.getIncidentNumber(), i.getTitle(), i.getSeverity(), i.getStatus(),
                        i.getReportedBy() != null ? i.getReportedBy().getUsername() : "",
                        i.getAssignedTo() != null ? i.getAssignedTo().getUsername() : "",
                        i.getCreatedAt().format(FMT));
            }
        }
        return out.toByteArray();
    }

    public byte[] generateIncidentsExcel() throws IOException {
        List<Incident> incidents = incidentRepository.findAll();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Incidents");
            String[] cols = {"Incident #", "Title", "Severity", "Status", "Reported By", "Assigned To", "Created At"};
            writeHeaderRow(sheet, cols);

            int rowIdx = 1;
            for (Incident i : incidents) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(i.getIncidentNumber());
                row.createCell(1).setCellValue(i.getTitle());
                row.createCell(2).setCellValue(i.getSeverity().name());
                row.createCell(3).setCellValue(i.getStatus().name());
                row.createCell(4).setCellValue(i.getReportedBy() != null ? i.getReportedBy().getUsername() : "");
                row.createCell(5).setCellValue(i.getAssignedTo() != null ? i.getAssignedTo().getUsername() : "Unassigned");
                row.createCell(6).setCellValue(i.getCreatedAt().format(FMT));
            }
            for (int c = 0; c < cols.length; c++) sheet.autoSizeColumn(c);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateIncidentsPdf(String generatedBy) throws IOException {
        List<Incident> incidents = incidentRepository.findAll();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(out);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            addBrandedHeader(document, "Incident Report", generatedBy);
            document.add(new Paragraph("Total incidents: " + incidents.size()).setFontSize(10).setItalic());
            document.add(new Paragraph("\n"));

            Table table = new Table(UnitValue.createPercentArray(new float[]{15, 30, 12, 15, 18, 15}))
                    .useAllAvailableWidth();
            for (String h : new String[]{"Incident #", "Title", "Severity", "Status", "Assigned To", "Created At"}) {
                table.addHeaderCell(headerCell(h));
            }
            for (Incident i : incidents) {
                table.addCell(i.getIncidentNumber());
                table.addCell(i.getTitle());
                table.addCell(i.getSeverity().name());
                table.addCell(i.getStatus().name());
                table.addCell(i.getAssignedTo() != null ? i.getAssignedTo().getUsername() : "Unassigned");
                table.addCell(i.getCreatedAt().format(FMT));
            }
            document.add(table);
        }
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- Audit Logs

    public byte[] generateAuditLogsCsv() throws IOException {
        List<AuditLog> logs = auditLogRepository.findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable.unpaged()).getContent();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8), CSVFormat.DEFAULT
                .builder().setHeader("ID", "Action", "Entity Type", "Entity ID", "Performed By", "IP Address", "Details", "Created At").build())) {
            for (AuditLog a : logs) {
                printer.printRecord(a.getId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                        a.getUser() != null ? a.getUser().getUsername() : "system", a.getIpAddress(),
                        a.getDetails(), a.getCreatedAt().format(FMT));
            }
        }
        return out.toByteArray();
    }

    public byte[] generateAuditLogsExcel() throws IOException {
        List<AuditLog> logs = auditLogRepository.findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable.unpaged()).getContent();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Audit Logs");
            String[] cols = {"ID", "Action", "Entity Type", "Entity ID", "Performed By", "IP Address", "Details", "Created At"};
            writeHeaderRow(sheet, cols);

            int rowIdx = 1;
            for (AuditLog a : logs) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(a.getId());
                row.createCell(1).setCellValue(a.getAction());
                row.createCell(2).setCellValue(a.getEntityType() != null ? a.getEntityType() : "");
                row.createCell(3).setCellValue(a.getEntityId() != null ? a.getEntityId() : 0);
                row.createCell(4).setCellValue(a.getUser() != null ? a.getUser().getUsername() : "system");
                row.createCell(5).setCellValue(a.getIpAddress() != null ? a.getIpAddress() : "");
                row.createCell(6).setCellValue(a.getDetails() != null ? a.getDetails() : "");
                row.createCell(7).setCellValue(a.getCreatedAt().format(FMT));
            }
            for (int c = 0; c < cols.length; c++) sheet.autoSizeColumn(c);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ---------------------------------------------------------------- Simulation History

    public byte[] generateSimulationsCsv() throws IOException {
        List<SimulationRun> runs = simulationRunRepository.findAll();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8), CSVFormat.DEFAULT
                .builder().setHeader("Simulation ID", "Source IP", "Destination IP", "Threat Category", "Country",
                        "User Role", "Device Type", "Traffic Type", "Result Threat Type", "Result Severity", "Run At").build())) {
            for (SimulationRun r : runs) {
                printer.printRecord(r.getId(), r.getSourceIp(), r.getDestinationIp(), r.getThreatCategory(), r.getCountry(),
                        r.getUserRole(), r.getDeviceType(), r.getTrafficType(),
                        r.getResultThreat() != null ? r.getResultThreat().getThreatType() : "",
                        r.getResultThreat() != null ? r.getResultThreat().getSeverity() : "",
                        r.getCreatedAt().format(FMT));
            }
        }
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- Enterprise Risk Summary

    public byte[] generateRiskSummaryPdf(String generatedBy) throws IOException {
        RiskScoreResponse risk = riskScoreService.computeRiskScore();
        SecuritySummaryResponse summary = securitySummaryService.generateSummary();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(out);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            addBrandedHeader(document, "Enterprise Risk Summary", generatedBy);

            document.add(new Paragraph("Overall Risk Score: " + risk.getOverallRisk() + "/100  (" + risk.getRiskLevel() + ")")
                    .setBold().setFontSize(13));
            document.add(new Paragraph("Security Posture: " + summary.getPostureLevel()).setFontSize(11));
            document.add(new Paragraph(summary.getSummary()).setFontSize(10));
            document.add(new Paragraph("\n"));

            document.add(new Paragraph("Risk Breakdown by Domain").setBold().setFontSize(12));
            Table table = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
            table.addHeaderCell(headerCell("Domain"));
            table.addHeaderCell(headerCell("Risk Score (0-100)"));
            addDomainRow(table, "Network", risk.getNetworkRisk());
            addDomainRow(table, "Host", risk.getHostRisk());
            addDomainRow(table, "Identity", risk.getIdentityRisk());
            addDomainRow(table, "Application", risk.getApplicationRisk());
            addDomainRow(table, "Data", risk.getDataRisk());
            document.add(table);
            document.add(new Paragraph("\n"));

            if (summary.getKeyFindings() != null && !summary.getKeyFindings().isEmpty()) {
                document.add(new Paragraph("Key Findings").setBold().setFontSize(12));
                for (String finding : summary.getKeyFindings()) {
                    document.add(new Paragraph("• " + finding).setFontSize(10));
                }
            }
        }
        return out.toByteArray();
    }

    private void addDomainRow(Table table, String domain, double score) {
        table.addCell(domain);
        table.addCell(String.valueOf(score));
    }

    // ---------------------------------------------------------------- Shared helpers

    /** Consistent CyberGuard-branded header for every PDF report: title, generated time, generated-by user. */
    private void addBrandedHeader(Document document, String reportTitle, String generatedBy) {
        document.add(new Paragraph("CyberGuard Platform").setBold().setFontSize(20).setFontColor(BRAND_COLOR));
        document.add(new Paragraph(reportTitle).setBold().setFontSize(15));
        document.add(new Paragraph("Generated: " + LocalDateTime.now().format(FMT) +
                (generatedBy != null ? "  |  By: " + generatedBy : "")).setFontSize(9).setItalic());
        document.add(new Paragraph("\n"));
    }

    private Cell headerCell(String text) {
        return new Cell().add(new Paragraph(text).setBold()).setBackgroundColor(new DeviceRgb(230, 230, 230));
    }

    private void writeHeaderRow(Sheet sheet, String[] cols) {
        Row header = sheet.createRow(0);
        for (int c = 0; c < cols.length; c++) header.createCell(c).setCellValue(cols[c]);
    }
}
