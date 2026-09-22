package com.cyberguard.platform.service;
import com.cyberguard.platform.controller.CollectorIngestionController;
import com.cyberguard.platform.config.SecurityConfig;
import com.cyberguard.platform.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.mockito.Mockito.*;

@WebMvcTest(controllers=CollectorIngestionController.class,properties={"app.collector.enabled=true","app.collector.ingest-key=only-a-test-key-with-at-least-32-characters"})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,AuthEntryPointJwt.class})
class CollectorIngestionSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean NetworkCollectorService service;
    @MockBean JwtUtil jwt;
    @MockBean CustomUserDetailsService details;
    static final String KEY="only-a-test-key-with-at-least-32-characters";
    static final String BODY="{\"collectorId\":\"windows-local\",\"sessionId\":\"12345678-1234-1234-1234-123456789abc\"}";
    @Test void ingestionRequiresDedicatedCredentialAndDirectLoopback() throws Exception {
        mvc.perform(post("/api/v1/collector/start").contentType("application/json").content(BODY).with(user("admin").roles("ADMIN"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/collector/start").header("X-Collector-Key",KEY).with(r->{r.setRemoteAddr("192.0.2.1");return r;}).contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/collector/start").header("X-Collector-Key",KEY).header("X-Forwarded-For","192.0.2.1").contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/collector/start").header("X-Collector-Key",KEY).contentType("application/json").content(BODY)).andExpect(status().isOk());
    }
    @Test void extraProvenanceAndOversizeInputsAreRejectedAndKeyCannotAccessOtherApis() throws Exception {
        mvc.perform(post("/api/v1/collector/start").header("X-Collector-Key",KEY).contentType("application/json").content(BODY.replace("}",",\"detectorType\":\"MODEL_A\"}"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/collector/observations").header("X-Collector-Key",KEY).contentType("application/json").content(" ".repeat(262145))).andExpect(status().isPayloadTooLarge());
        mvc.perform(get("/api/v1/monitoring/security-events").header("X-Collector-Key",KEY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
