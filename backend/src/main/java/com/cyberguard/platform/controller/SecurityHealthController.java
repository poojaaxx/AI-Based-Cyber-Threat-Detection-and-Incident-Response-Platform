package com.cyberguard.platform.controller;

import com.cyberguard.platform.dto.response.SecurityHealthResponse;
import com.cyberguard.platform.service.SecurityHealthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security-health")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
@Tag(name = "Security Health", description = "Authentication health, account status, risk posture, and system reachability")
public class SecurityHealthController {

    private final SecurityHealthService securityHealthService;

    @GetMapping
    public ResponseEntity<SecurityHealthResponse> getSecurityHealth() {
        return ResponseEntity.ok(securityHealthService.getSecurityHealth());
    }
}
