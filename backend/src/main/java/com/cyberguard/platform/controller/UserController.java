package com.cyberguard.platform.controller;

import com.cyberguard.platform.dto.request.CreateUserRequest;
import com.cyberguard.platform.dto.request.UpdateUserRequest;
import com.cyberguard.platform.dto.response.ResetPasswordResponse;
import com.cyberguard.platform.dto.response.UserResponse;
import com.cyberguard.platform.entity.User;
import com.cyberguard.platform.entity.enums.UserStatus;
import com.cyberguard.platform.security.CustomUserDetails;
import com.cyberguard.platform.service.UserService;
import com.cyberguard.platform.util.RequestUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Admin user management and role-based access control")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(userService.toResponse(principal.getUser()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String role,
            @RequestParam(required = false, defaultValue = "username") String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<User> users = userService.search(search, status, department, role, sortBy, sortDir,
                pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(users.map(userService::toResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.toResponse(userService.getUserOrThrow(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                     @AuthenticationPrincipal CustomUserDetails principal,
                                                     HttpServletRequest httpRequest) {
        User user = userService.createUser(request, principal.getUser(), RequestUtil.extractIp(httpRequest));
        return ResponseEntity.ok(userService.toResponse(user));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
                                                     @AuthenticationPrincipal CustomUserDetails principal,
                                                     HttpServletRequest httpRequest) {
        User user = userService.updateUser(id, request, principal.getUser(), RequestUtil.extractIp(httpRequest));
        return ResponseEntity.ok(userService.toResponse(user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                                             HttpServletRequest httpRequest) {
        userService.deleteUser(id, principal.getUser(), RequestUtil.extractIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@PathVariable Long id,
                                                                  @AuthenticationPrincipal CustomUserDetails principal,
                                                                  HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userService.resetPassword(id, principal.getUser(), RequestUtil.extractIp(httpRequest)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateStatus(@PathVariable Long id, @RequestParam UserStatus status,
                                                        @AuthenticationPrincipal CustomUserDetails principal,
                                                        HttpServletRequest httpRequest) {
        User user = userService.updateStatus(id, status, principal.getUser(), RequestUtil.extractIp(httpRequest));
        return ResponseEntity.ok(userService.toResponse(user));
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateRoles(@PathVariable Long id, @RequestBody Set<String> roles,
                                                       @AuthenticationPrincipal CustomUserDetails principal,
                                                       HttpServletRequest httpRequest) {
        User user = userService.updateRoles(id, roles, principal.getUser(), RequestUtil.extractIp(httpRequest));
        return ResponseEntity.ok(userService.toResponse(user));
    }
}
