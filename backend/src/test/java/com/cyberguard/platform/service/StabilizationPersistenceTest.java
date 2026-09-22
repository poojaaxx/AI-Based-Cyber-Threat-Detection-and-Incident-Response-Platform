package com.cyberguard.platform.service;

import com.cyberguard.platform.client.AiServiceClient;
import com.cyberguard.platform.dto.request.LoginRequest;
import com.cyberguard.platform.entity.*;
import com.cyberguard.platform.entity.enums.*;
import com.cyberguard.platform.repository.*;
import com.cyberguard.platform.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "app.auth.max-failed-attempts=3", "app.auth.lockout-duration-minutes=2"})
@Import({AuthService.class, CustomUserDetailsService.class, IncidentService.class,
    ResponseActionService.class, AuthenticationMonitoringService.class, ThreatService.class, StabilizationPersistenceTest.AuthenticationConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StabilizationPersistenceTest {
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired LoginAttemptRepository attempts;
    @Autowired ThreatRepository threats;
    @Autowired IncidentRepository incidents;
    @Autowired IncidentTimelineRepository timeline;
    @Autowired ResponseActionRepository actions;
    @Autowired NotificationRepository notificationRows;
    @Autowired IncidentService incidentService;
    @Autowired ResponseActionService responses;
    @Autowired PasswordEncoder encoder;
    @MockBean JwtUtil jwt;
    @MockBean AuditLogService audit;
    @MockBean NotificationService notifications;
    @MockBean AiServiceClient ai;
    @MockBean SseHubService sse;
    @Autowired SecurityEventRepository events;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @TestConfiguration
    static class AuthenticationConfig {
        @Bean com.fasterxml.jackson.databind.ObjectMapper objectMapper() { return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(); }
        @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(4); }
        @Bean AuthenticationManager authenticationManager(CustomUserDetailsService details, PasswordEncoder encoder) {
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
            provider.setUserDetailsService(details);
            provider.setPasswordEncoder(encoder);
            return new ProviderManager(provider);
        }
    }

    User newUser() {
        String name = "test-" + UUID.randomUUID();
        return users.saveAndFlush(User.builder().username(name).email(name + "@example.test")
            .fullName("Test User").passwordHash(encoder.encode("test-secret")).build());
    }
    void login(User u, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail(u.getUsername()); request.setPassword(password);
        auth.login(request, "127.0.0.1", "test");
    }
    User reload(User u) { return users.findById(u.getId()).orElseThrow(); }
    void fail(User u) { assertThrows(BadCredentialsException.class, () -> login(u, "wrong")); }

    @Test void lockoutPersistsAcrossTransactionsAndExpiresSafely() {
        User u = newUser();
        long count = attempts.count();
        fail(u); assertEquals(1, reload(u).getFailedLoginAttempts());
        fail(u); fail(u);
        User locked = reload(u);
        assertEquals(UserStatus.LOCKED, locked.getStatus());
        assertNotNull(locked.getLockedUntil());
        assertTrue(locked.getLockedUntil().isAfter(LocalDateTime.now().plusMinutes(1)));
        assertThrows(LockedException.class, () -> login(u, "test-secret"));
        assertEquals(locked.getLockedUntil(), reload(u).getLockedUntil());
        assertEquals(count + 4, attempts.count());
        locked.setLockedUntil(LocalDateTime.now().minusSeconds(1)); users.saveAndFlush(locked);
        login(u, "test-secret");
        User recovered = reload(u);
        assertEquals(UserStatus.ACTIVE, recovered.getStatus());
        assertEquals(0, recovered.getFailedLoginAttempts());
        assertNull(recovered.getLockedUntil());
        assertNotNull(recovered.getLastLoginAt());
    }
    @Test void successfulLoginResetsFailuresAndExpiredWrongPasswordStartsNewWindow() {
        User u = newUser(); fail(u); fail(u); login(u, "test-secret");
        assertEquals(0, reload(u).getFailedLoginAttempts());
        fail(u); assertEquals(1, reload(u).getFailedLoginAttempts());
        fail(u); fail(u);
        User locked = reload(u); locked.setLockedUntil(LocalDateTime.now().minusSeconds(1)); users.saveAndFlush(locked);
        fail(u);
        assertEquals(1, reload(u).getFailedLoginAttempts());
        assertNull(reload(u).getLockedUntil());
        assertEquals(UserStatus.ACTIVE, reload(u).getStatus());
    }
    @Test void expirationDoesNotReactivateDisabledOrAdministrativeLocks() {
        User u = newUser(); u.setStatus(UserStatus.DISABLED);
        u.setLockedUntil(LocalDateTime.now().minusSeconds(1)); users.saveAndFlush(u);
        assertThrows(DisabledException.class, () -> login(u, "test-secret"));
        assertEquals(UserStatus.DISABLED, reload(u).getStatus());
        User manualLock = reload(u); manualLock.setStatus(UserStatus.LOCKED); users.saveAndFlush(manualLock);
        assertThrows(LockedException.class, () -> login(manualLock, "test-secret"));
        assertEquals(UserStatus.LOCKED, reload(u).getStatus());
    }
    Threat newThreat() {
        return threats.saveAndFlush(Threat.builder().threatType(ThreatType.MALWARE)
            .severity(Severity.HIGH).sourceIp("192.0.2.20")
            .confidenceScore(java.math.BigDecimal.valueOf(90)).build());
    }
    @Test void automatedIncidentHasNullableSystemReporterWithoutAdmin() {
        assertFalse(users.findByUsername("admin").isPresent());
        Incident result = incidentService.createAutomatedIncident(newThreat());
        assertNull(incidents.findById(result.getId()).orElseThrow().getReportedBy());
        assertEquals(1, timeline.findByIncidentIdOrderByCreatedAtAsc(result.getId()).size());
    }
    @Test void quarantineUpdatesDatabaseAndBlockRequestRemainsPending() {
        Threat threat = newThreat();
        ResponseAction quarantine = responses.quarantineThreat(threat, null);
        assertEquals(ThreatStatus.MITIGATED, threats.findById(threat.getId()).orElseThrow().getStatus());
        assertEquals(ActionStatus.SUCCESS, actions.findById(quarantine.getId()).orElseThrow().getStatus());
        assertTrue(quarantine.getDetails().contains("no host or network isolation"));
        ResponseAction block = responses.blockIp("192.0.2.20", "test", null, threat, null);
        assertEquals(ActionStatus.PENDING, actions.findById(block.getId()).orElseThrow().getStatus());
        assertTrue(block.getDetails().contains("not configured"));
    }
    @Test void nonexistentQuarantineCannotRecordSuccess() {
        long count = actions.count();
        assertThrows(com.cyberguard.platform.exception.ResourceNotFoundException.class,
            () -> responses.quarantineThreat(Threat.builder().id(Long.MAX_VALUE).build(), null));
        assertEquals(count, actions.count());
    }
    @Test void noAdminRecipientsRecordsFailedNotification() {
        Threat threat = newThreat(); threat.setSeverity(Severity.LOW); threats.saveAndFlush(threat);
        responses.autoRespond(threat);
        assertTrue(actions.findAll().stream().anyMatch(a -> a.getActionType() == ActionType.NOTIFY_ADMIN
            && a.getStatus() == ActionStatus.FAILED));
    }

    @Test void concurrentFailuresReachThresholdWithoutLostIncrements() throws Exception {
        User u = newUser();
        long threatCount = threats.count();
        long incidentCount = incidents.count();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(3);
        try {
            java.util.List<java.util.concurrent.Future<?>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) results.add(pool.submit(() -> fail(u)));
            for (var result : results) result.get(20, java.util.concurrent.TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(3, reload(u).getFailedLoginAttempts());
        assertEquals(UserStatus.LOCKED, reload(u).getStatus());
        assertNotNull(reload(u).getLockedUntil());
        assertEquals(threatCount + 1, threats.count());
        assertEquals(incidentCount + 1, incidents.count());
    }

    @Test void realAuthenticationEventsHaveRuleEvidenceAndExactlyOneIncidentWithoutAi() throws Exception {
        User u = newUser();
        long threatCount = threats.count();
        long incidentCount = incidents.count();
        responses.setAdaptiveModeEnabled(true); // RULE must still bypass FastAPI entirely.
        try {
            login(u, "test-secret");
            fail(u);
            assertEquals(threatCount, threats.count());
            assertEquals(incidentCount, incidents.count());
            fail(u); fail(u);
            for (int i = 0; i < 3; i++) assertThrows(LockedException.class, () -> login(u, "wrong"));
            var rows = events.findAll().stream().filter(e -> u.getUsername().equals(e.getUsername())).toList();
            assertEquals(7, rows.size());
            assertEquals(1, rows.stream().filter(e -> "SUCCESS".equals(e.getOutcome())).count());
            assertEquals(3, rows.stream().filter(e -> "LOCKED".equals(e.getOutcome())).count());
            var finding = rows.stream().filter(e -> e.getThreatId() != null).findFirst().orElseThrow();
            Threat threat = threats.findById(finding.getThreatId()).orElseThrow();
            assertEquals("RULE", threat.getDetectorType());
            assertNull(threat.getConfidenceScore());
            assertNull(threat.getTemporalCategory());
            assertNull(threat.getCrossModelAgreement());
            assertEquals(finding.getLoginAttemptId(), threat.getLoginAttemptId());
            assertNotNull(finding.getIncidentId());
            assertNotNull(finding.getObservedAt());
            assertNotNull(finding.getIngestedAt());
            assertNotNull(finding.getDetectedAt());
            assertTrue(finding.getProcessingLatencyMs() >= 0);
            var evidence = new com.fasterxml.jackson.databind.ObjectMapper().readTree(finding.getEvidence());
            assertEquals(3, evidence.get("threshold").asInt());
            assertEquals(3, evidence.get("attempts").size());
            for (var row : evidence.get("attempts")) {
                assertFalse(attempts.findById(row.get("id").asLong()).orElseThrow().getSuccess());
            }
            assertEquals(threatCount + 1, threats.count());
            assertEquals(incidentCount + 1, incidents.count());
            assertTrue(actions.findByThreatIdOrderByCreatedAtAsc(threat.getId()).stream()
                    .anyMatch(a -> a.getActionType() == ActionType.GENERATE_INCIDENT));
            verifyNoInteractions(ai);
            verify(notifications, atLeastOnce()).notifyAllAdmins(anyString(), contains("Detector: RULE"), any(), any(), anyString());
        } finally { responses.setAdaptiveModeEnabled(false); }
    }

    @Test void committedNotificationsAndSecurityEventsAreDeliveredButRollbackIsSilent() {
        User user = newUser();
        var delivered = new java.util.ArrayList<String>();
        SseHubService hub = new SseHubService() {
            @Override protected org.springframework.web.servlet.mvc.method.annotation.SseEmitter createEmitter() {
                return new org.springframework.web.servlet.mvc.method.annotation.SseEmitter() {
                    @Override public void send(SseEventBuilder builder) {
                        delivered.add(builder.build().stream().map(part -> String.valueOf(part.getData()))
                                .collect(java.util.stream.Collectors.joining()));
                    }
                };
            }
        };
        hub.subscribe(user.getId(), true);
        delivered.clear();
        var notificationsService = new NotificationService(notificationRows, users, mock(EmailService.class), hub);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        long previous = notificationRows.count();
        tx.executeWithoutResult(status -> {
            notificationsService.notifyUser(user, "Committed", "Real persisted notification", NotificationType.THREAT);
            hub.publishSecurityEvent(SecurityEvent.builder().id(123L).build());
            assertTrue(delivered.isEmpty());
        });
        assertEquals(previous + 1, notificationRows.count());
        assertTrue(delivered.stream().anyMatch(s -> s.contains("notification")));
        assertTrue(delivered.stream().anyMatch(s -> s.contains("security-event")));
        delivered.clear();
        tx.executeWithoutResult(status -> {
            notificationsService.notifyUser(user, "Rolled back", "Must never reach browser", NotificationType.THREAT);
            hub.publishSecurityEvent(SecurityEvent.builder().id(124L).build());
            assertTrue(delivered.isEmpty());
            status.setRollbackOnly();
        });
        assertTrue(delivered.isEmpty());
        assertEquals(previous + 1, notificationRows.count());
    }
    @Test void notificationOwnerQueryAndReadPersistToDatabase() {
        User owner = newUser(); User other = newUser();
        Notification row = notificationRows.saveAndFlush(Notification.builder().user(owner)
            .title("Test").message("Test").type(NotificationType.SYSTEM).build());
        NotificationService service = new NotificationService(notificationRows, users,
            mock(EmailService.class), mock(SseHubService.class));
        assertThrows(com.cyberguard.platform.exception.ResourceNotFoundException.class,
            () -> service.markAsRead(row.getId(), other.getId()));
        assertFalse(notificationRows.findById(row.getId()).orElseThrow().getIsRead());
        service.markAsRead(row.getId(), owner.getId());
        assertTrue(notificationRows.findById(row.getId()).orElseThrow().getIsRead());
    }
    @Test void disableUserSuccessMeansDatabaseAccountIsDisabled() {
        User u = newUser();
        ResponseAction result = responses.disableUser(u.getId(), "test", null, null);
        assertEquals(UserStatus.DISABLED, reload(u).getStatus());
        assertEquals(ActionStatus.SUCCESS, actions.findById(result.getId()).orElseThrow().getStatus());
        assertThrows(DisabledException.class, () -> login(u, "test-secret"));
    }
    @Test void invalidLockoutConfigurationIsRejected() {
        AuthService candidate = new AuthService(users, null, null, null, null, null, null, null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(candidate, "maxFailedAttempts", 0);
        org.springframework.test.util.ReflectionTestUtils.setField(candidate, "lockoutDurationMinutes", 2);
        assertThrows(IllegalStateException.class, candidate::validateLockoutConfiguration);
        org.springframework.test.util.ReflectionTestUtils.setField(candidate, "maxFailedAttempts", 3);
        org.springframework.test.util.ReflectionTestUtils.setField(candidate, "lockoutDurationMinutes", 0);
        assertThrows(IllegalStateException.class, candidate::validateLockoutConfiguration);
    }
}
