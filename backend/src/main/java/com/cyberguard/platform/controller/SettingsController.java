package com.cyberguard.platform.controller;

import com.cyberguard.platform.dto.request.ChangePasswordRequest;
import com.cyberguard.platform.dto.request.NotificationPreferenceRequest;
import com.cyberguard.platform.dto.request.UpdateProfileRequest;
import com.cyberguard.platform.dto.response.AdaptiveModeResponse;
import com.cyberguard.platform.dto.response.MessageResponse;
import com.cyberguard.platform.entity.ApiConfiguration;
import com.cyberguard.platform.entity.NotificationPreference;
import com.cyberguard.platform.security.CustomUserDetails;
import com.cyberguard.platform.service.ResponseActionService;
import com.cyberguard.platform.service.SettingsService;
import com.cyberguard.platform.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "User profile, password, notification preferences and API configuration")
public class SettingsController {

    private final SettingsService settingsService;
    private final UserService userService;
    private final ResponseActionService responseActionService;

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request,
                                            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(userService.toResponse(settingsService.updateProfile(principal.getId(), request)));
    }

    @PutMapping("/password")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                            @AuthenticationPrincipal CustomUserDetails principal) {
        settingsService.changePassword(principal.getId(), request);
        return ResponseEntity.ok(new MessageResponse("Password updated successfully"));
    }

    @GetMapping("/notification-preferences")
    public ResponseEntity<NotificationPreference> getNotificationPreferences(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(settingsService.getNotificationPreferences(principal.getId()));
    }

    @PutMapping("/notification-preferences")
    public ResponseEntity<NotificationPreference> updateNotificationPreferences(
            @RequestBody NotificationPreferenceRequest request, @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(settingsService.updateNotificationPreferences(principal.getId(), request));
    }

    @GetMapping("/api-configuration")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiConfiguration> getApiConfiguration(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(settingsService.getApiConfiguration(principal.getId()));
    }

    @PostMapping("/api-configuration/regenerate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiConfiguration> regenerateApiKey(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(settingsService.regenerateApiKey(principal.getId()));
    }

    /** Readable by any authenticated user (not sensitive), so the Settings page can show the
     * current mode even to non-admins; only ADMIN can flip it (see PUT below). */
    @GetMapping("/adaptive-response-mode")
    public ResponseEntity<AdaptiveModeResponse> getAdaptiveResponseMode() {
        return ResponseEntity.ok(new AdaptiveModeResponse(responseActionService.isAdaptiveModeEnabled()));
    }

    @PutMapping("/adaptive-response-mode")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdaptiveModeResponse> setAdaptiveResponseMode(@RequestBody AdaptiveModeResponse request) {
        responseActionService.setAdaptiveModeEnabled(request.isEnabled());
        return ResponseEntity.ok(new AdaptiveModeResponse(responseActionService.isAdaptiveModeEnabled()));
    }
}
