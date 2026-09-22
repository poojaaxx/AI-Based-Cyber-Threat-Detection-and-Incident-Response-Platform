package com.cyberguard.platform.service;

import com.cyberguard.platform.repository.*;
import com.cyberguard.platform.dto.response.*;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {
    @Test void generatesReadableThreatIncidentAndRiskPdfsAtRuntime() throws Exception {
        RiskScoreService risk = mock(RiskScoreService.class);
        SecuritySummaryService summary = mock(SecuritySummaryService.class);
        when(risk.computeRiskScore()).thenReturn(RiskScoreResponse.builder().riskLevel("LOW").build());
        when(summary.generateSummary()).thenReturn(SecuritySummaryResponse.builder()
            .postureLevel("LOW").summary("Test summary").build());
        ReportService service = new ReportService(mock(ThreatRepository.class), mock(IncidentRepository.class),
            mock(SimulationRunRepository.class), mock(AuditLogRepository.class), risk, summary);
        byte[][] reports = {service.generateThreatsPdf("test"), service.generateIncidentsPdf("test"),
            service.generateRiskSummaryPdf("test")};
        for (byte[] bytes : reports) {
            try (PdfDocument pdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(bytes)))) {
                assertTrue(pdf.getNumberOfPages() > 0);
                assertTrue(PdfTextExtractor.getTextFromPage(pdf.getFirstPage()).contains("CyberGuard Platform"));
            }
        }
    }
}
