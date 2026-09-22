package com.cyberguard.platform.service;

import com.cyberguard.platform.controller.*;
import com.cyberguard.platform.config.SecurityConfig;
import com.cyberguard.platform.client.AiServiceClient;
import com.cyberguard.platform.entity.*;
import com.cyberguard.platform.repository.*;
import com.cyberguard.platform.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ThreatController.class, NotificationController.class, MonitoringController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, AuthEntryPointJwt.class, NotificationService.class})
class StabilizationWebTest {
    @Autowired MockMvc mvc;
    @MockBean ThreatService threats;
    @MockBean ThreatInvestigationService investigation;
    @MockBean AiServiceClient ai;
    @MockBean CustomUserDetailsService details;
    @MockBean JwtUtil jwt;
    @MockBean NotificationRepository notifications;
    @MockBean UserRepository users;
    @MockBean EmailService email;
    @MockBean SseHubService sse;
    @MockBean MonitoringService monitoring;
    @MockBean NetworkCollectorService networkCollector;
    @MockBean SecurityEventRepository securityEvents;

    @Test void liveHistoryIsRestrictedToMonitoringRoles() throws Exception {
        mvc.perform(get("/api/v1/monitoring/security-events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/monitoring/security-events").with(user("reader").roles("USER"))).andExpect(status().isForbidden());
        for (String role : new String[]{"ADMIN", "ANALYST"}) {
            mvc.perform(get("/api/v1/monitoring/security-events").with(user("reader").roles(role))).andExpect(status().isOk());
        }
    }

    @Test void threatReadsRequireKnownRolesAndPreserveStandardUserAccess() throws Exception {
        String[] paths = {"", "/recent", "/1", "/1/explain", "/1/investigation"};
        for (String path : paths) {
            String url = "/api/v1/threats" + path;
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).with(user("outsider").roles("OTHER"))).andExpect(status().isForbidden());
            for (String role : new String[]{"ADMIN", "ANALYST", "USER"}) {
                mvc.perform(get(url).with(user("reader").roles(role))).andExpect(status().isOk());
            }
        }
    }
    @Test void standardUserCannotMutateThreatsOrInvokeTemporalPrediction() throws Exception {
        mvc.perform(patch("/api/v1/threats/1/status?status=MITIGATED").with(user("reader").roles("USER")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/threats/predict-temporal").contentType("application/json").content("{}")
            .with(user("reader").roles("USER"))).andExpect(status().isForbidden());
        for (String role : new String[]{"ADMIN", "ANALYST"}) {
            mvc.perform(patch("/api/v1/threats/1/status?status=MITIGATED").with(user("operator").roles(role)))
                .andExpect(status().isOk());
        }
    }
    @Test void notificationReadChecksAuthenticatedOwner() throws Exception {
        User owner = User.builder().id(1L).username("owner").build();
        User other = User.builder().id(2L).username("other").build();
        Notification notification = Notification.builder().id(10L).user(owner).build();
        when(notifications.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(notification));
        when(notifications.findByIdAndUserId(10L, 2L)).thenReturn(Optional.empty());
        mvc.perform(patch("/api/v1/notifications/10/read").with(user(new CustomUserDetails(other))))
            .andExpect(status().isNotFound());
        verify(notifications, never()).save(any());
        mvc.perform(patch("/api/v1/notifications/10/read").with(user(new CustomUserDetails(owner))))
            .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(notification.getIsRead());
        verify(notifications).save(notification);
    }

    @Test void existingJwtCannotBypassDisabledOrLockedAccount() throws Exception {
        when(jwt.isTokenValid("test-token")).thenReturn(true);
        when(jwt.extractUsername("test-token")).thenReturn("test");
        for (com.cyberguard.platform.entity.enums.UserStatus status : new com.cyberguard.platform.entity.enums.UserStatus[]{
                com.cyberguard.platform.entity.enums.UserStatus.DISABLED,
                com.cyberguard.platform.entity.enums.UserStatus.LOCKED}) {
            when(details.loadUserByUsername("test")).thenReturn(new CustomUserDetails(
                User.builder().username("test").status(status).build()));
            mvc.perform(get("/api/v1/threats/recent").header("Authorization", "Bearer test-token"))
                .andExpect(status().isUnauthorized());
        }
    }
}
