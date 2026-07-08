package com.cyberguard.platform.controller;

import com.cyberguard.platform.dto.request.ManualActionRequest;
import com.cyberguard.platform.entity.ResponseAction;
import com.cyberguard.platform.security.CustomUserDetails;
import com.cyberguard.platform.service.ResponseActionService;
import com.cyberguard.platform.service.ThreatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/response-actions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
@Tag(name = "Automated Incident Response", description = "Block IP, disable user, quarantine threat, manual response actions")
public class ResponseActionController {

    private final ResponseActionService responseActionService;
    private final ThreatService threatService;

    @GetMapping
    public ResponseEntity<Page<ResponseAction>> getActions(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(responseActionService.getActions(pageable));
    }

    @PostMapping("/block-ip")
    public ResponseEntity<ResponseAction> blockIp(@Valid @RequestBody ManualActionRequest request,
                                                    @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(responseActionService.blockIp(
                request.getTarget(), request.getReason(), null, null, principal.getUser()));
    }

    @PostMapping("/disable-user/{userId}")
    public ResponseEntity<ResponseAction> disableUser(@PathVariable Long userId, @RequestBody ManualActionRequest request,
                                                        @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(responseActionService.disableUser(userId, request.getReason(), null, principal.getUser()));
    }

    @PostMapping("/quarantine/{threatId}")
    public ResponseEntity<ResponseAction> quarantine(@PathVariable Long threatId,
                                                       @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(responseActionService.quarantineThreat(
                threatService.getThreatOrThrow(threatId), principal.getUser()));
    }
}
