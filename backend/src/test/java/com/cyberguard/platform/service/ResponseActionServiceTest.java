package com.cyberguard.platform.service;

import com.cyberguard.platform.client.AiServiceClient;
import com.cyberguard.platform.dto.response.PolicyRecommendationResponse;
import com.cyberguard.platform.entity.BlockedIp;
import com.cyberguard.platform.entity.Incident;
import com.cyberguard.platform.entity.ResponseAction;
import com.cyberguard.platform.entity.Threat;
import com.cyberguard.platform.entity.enums.Severity;
import com.cyberguard.platform.entity.enums.ThreatType;
import com.cyberguard.platform.repository.BlockedIpRepository;
import com.cyberguard.platform.repository.ResponseActionRepository;
import com.cyberguard.platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Phase 4 pass-criteria checks: autoRespondAdaptive() falls back to the
 * static autoRespond() playbook when the AI service is unreachable, and
 * autoRespond() itself still works unchanged after the Phase 4 additions.
 */
class ResponseActionServiceTest {

    private ResponseActionRepository responseActionRepository;
    private BlockedIpRepository blockedIpRepository;
    private UserRepository userRepository;
    private IncidentService incidentService;
    private NotificationService notificationService;
    private AuditLogService auditLogService;
    private AiServiceClient aiServiceClient;
    private ResponseActionService service;

    @BeforeEach
    void setUp() {
        responseActionRepository = mock(ResponseActionRepository.class);
        blockedIpRepository = mock(BlockedIpRepository.class);
        userRepository = mock(UserRepository.class);
        incidentService = mock(IncidentService.class);
        notificationService = mock(NotificationService.class);
        auditLogService = mock(AuditLogService.class);
        aiServiceClient = mock(AiServiceClient.class);

        service = new ResponseActionService(
                responseActionRepository, blockedIpRepository, userRepository,
                incidentService, notificationService, auditLogService, aiServiceClient
        );

        when(responseActionRepository.save(any(ResponseAction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(blockedIpRepository.findByIpAddress(anyString())).thenReturn(Optional.empty());
        when(blockedIpRepository.save(any(BlockedIp.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Threat highSeverityThreat() {
        return Threat.builder()
                .id(1L)
                .threatType(ThreatType.MALWARE)
                .severity(Severity.HIGH)
                .confidenceScore(BigDecimal.valueOf(80))
                .sourceIp("1.2.3.4")
                .build();
    }

    @Test
    void autoRespondAdaptive_fallsBackToStaticPlaybook_whenAiServiceUnreachable() {
        Threat threat = highSeverityThreat();
        Incident fallbackIncident = Incident.builder().incidentNumber("INC-FALLBACK").build();
        when(incidentService.createAutomatedIncident(threat)).thenReturn(fallbackIncident);
        when(aiServiceClient.recommendAction(anyString(), anyString(), anyDouble()))
                .thenThrow(new RuntimeException("AI service unreachable"));

        Incident result = service.autoRespondAdaptive(threat);

        verify(aiServiceClient, times(1)).recommendAction(anyString(), anyString(), anyDouble());
        // Falling back to autoRespond() means the STATIC playbook's incident
        // creation ran (not executeRecommendedAction()'s ESCALATE branch),
        // and the source IP still got blocked per the HIGH-severity playbook.
        assertSame(fallbackIncident, result);
        verify(blockedIpRepository, times(1)).save(any(BlockedIp.class));
        verify(incidentService, times(1)).createAutomatedIncident(threat);
    }

    @Test
    void autoRespondAdaptive_executesRecommendedAction_whenAiServiceReachable() {
        Threat threat = highSeverityThreat();
        when(aiServiceClient.recommendAction(anyString(), anyString(), anyDouble()))
                .thenReturn(new PolicyRecommendationResponse("MALWARE|HIGH|HIGH", "QUARANTINE", Map.of("QUARANTINE", 5.0)));

        Incident result = service.autoRespondAdaptive(threat);

        // QUARANTINE recommendation: no incident created, no IP block, no fallback to autoRespond().
        assertEquals(null, result);
        verify(incidentService, never()).createAutomatedIncident(any());
        verify(blockedIpRepository, never()).save(any(BlockedIp.class));
    }

    @Test
    void autoRespond_stillWorksUnchanged_forHighSeverityThreat() {
        Threat threat = highSeverityThreat();
        Incident incident = Incident.builder().incidentNumber("INC-1").build();
        when(incidentService.createAutomatedIncident(threat)).thenReturn(incident);

        Incident result = service.autoRespond(threat);

        assertSame(incident, result);
        verify(blockedIpRepository, times(1)).save(any(BlockedIp.class));
        verify(incidentService, times(1)).createAutomatedIncident(threat);
        verify(notificationService, times(1)).notifyAllAdmins(
                anyString(), anyString(), any(), any(), anyString());
    }
}
