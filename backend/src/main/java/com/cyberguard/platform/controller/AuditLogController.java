package com.cyberguard.platform.controller;

import com.cyberguard.platform.entity.AuditLog;
import com.cyberguard.platform.service.AuditLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
@Tag(name = "Audit Logs", description = "System-wide audit trail of important actions")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<Page<AuditLog>> getAuditLogs(@PageableDefault(size = 25) Pageable pageable) {
        // Ordering is fixed (newest first) by the repository's own JPQL query, so no
        // `sort` is requested here - avoids Spring Data appending a conflicting ORDER BY.
        return ResponseEntity.ok(auditLogService.getAuditLogs(pageable));
    }
}
