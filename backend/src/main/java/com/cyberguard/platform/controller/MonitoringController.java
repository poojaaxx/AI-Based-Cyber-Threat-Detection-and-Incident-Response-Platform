package com.cyberguard.platform.controller;

import com.cyberguard.platform.entity.LoginAttempt;
import com.cyberguard.platform.entity.NetworkEvent;
import com.cyberguard.platform.entity.SystemLog;
import com.cyberguard.platform.service.MonitoringService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
@Tag(name = "Real-Time Monitoring", description = "System logs, login monitoring and network event feed")
public class MonitoringController {

    private final MonitoringService monitoringService;

    @GetMapping("/system-logs")
    public ResponseEntity<Page<SystemLog>> getSystemLogs(@PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(monitoringService.getSystemLogs(pageable));
    }

    @GetMapping("/login-attempts")
    public ResponseEntity<Page<LoginAttempt>> getLoginAttempts(@PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(monitoringService.getLoginAttempts(pageable));
    }

    @GetMapping("/network-events")
    public ResponseEntity<Page<NetworkEvent>> getNetworkEvents(@RequestParam(defaultValue = "false") boolean flaggedOnly,
                                                                 @PageableDefault(size = 25) Pageable pageable) {
        return ResponseEntity.ok(monitoringService.getNetworkEvents(flaggedOnly, pageable));
    }
}
