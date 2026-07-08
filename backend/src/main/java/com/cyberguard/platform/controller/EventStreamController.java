package com.cyberguard.platform.controller;

import com.cyberguard.platform.security.CustomUserDetails;
import com.cyberguard.platform.service.SseHubService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live event stream (Server-Sent Events). Authenticated exactly like every other
 * endpoint - the frontend connects with `fetch()` + a normal Authorization header
 * rather than the native EventSource API, so no changes to JWT/security config
 * were needed to support this.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Real-Time Events", description = "Server-Sent Events stream for live dashboard updates and notifications")
public class EventStreamController {

    private final SseHubService sseHubService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal CustomUserDetails principal) {
        return sseHubService.subscribe(principal.getId());
    }
}
