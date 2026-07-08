package com.cyberguard.platform.service;

import com.cyberguard.platform.dto.request.LoginRequest;
import com.cyberguard.platform.dto.request.RefreshTokenRequest;
import com.cyberguard.platform.dto.request.RegisterRequest;
import com.cyberguard.platform.dto.response.JwtResponse;
import com.cyberguard.platform.entity.*;
import com.cyberguard.platform.entity.enums.UserStatus;
import com.cyberguard.platform.exception.BadRequestException;
import com.cyberguard.platform.exception.ResourceNotFoundException;
import com.cyberguard.platform.repository.*;
import com.cyberguard.platform.security.CustomUserDetails;
import com.cyberguard.platform.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final AuditLogService auditLogService;
    private final LoginAttemptRepository loginAttemptRepository;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public JwtResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        // Public self-registration always creates a standard USER account. Elevated
        // roles (ANALYST/ADMIN) can only be granted by an admin via the User Management API.
        Role role = roleRepository.findByName(Role.USER)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + Role.USER));

        Set<Role> roles = new HashSet<>();
        roles.add(role);

        User user = User.builder()
                .fullName(request.getFullName())
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .department(request.getDepartment())
                .phone(request.getPhone())
                .status(UserStatus.ACTIVE)
                .roles(roles)
                .build();

        user = userRepository.save(user);

        notificationPreferenceRepository.save(
                NotificationPreference.builder().user(user).build()
        );

        auditLogService.log(user, "USER_REGISTERED", "User", user.getId(), "New account created", null);

        return buildJwtResponse(user);
    }

    @Transactional
    public JwtResponse login(LoginRequest request, String ipAddress, String userAgent) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword())
            );
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            user.setLastLoginAt(LocalDateTime.now());
            user.setFailedLoginAttempts(0);
            userRepository.save(user);

            loginAttemptRepository.save(LoginAttempt.builder()
                    .user(user).usernameAttempted(request.getUsernameOrEmail())
                    .ipAddress(ipAddress).success(true).userAgent(userAgent).build());

            auditLogService.log(user, "LOGIN_SUCCESS", "User", user.getId(), "Successful login", ipAddress);

            return buildJwtResponse(user);
        } catch (BadCredentialsException ex) {
            loginAttemptRepository.save(LoginAttempt.builder()
                    .usernameAttempted(request.getUsernameOrEmail())
                    .ipAddress(ipAddress).success(false).userAgent(userAgent).build());
            userRepository.findByUsernameOrEmail(request.getUsernameOrEmail(), request.getUsernameOrEmail())
                    .ifPresent(u -> {
                        u.setFailedLoginAttempts(u.getFailedLoginAttempts() + 1);
                        userRepository.save(u);
                    });
            throw ex;
        }
    }

    @Transactional
    public JwtResponse refresh(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (token.getRevoked() || token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Refresh token expired or revoked. Please log in again.");
        }

        User user = token.getUser();
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        return buildJwtResponse(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    private JwtResponse buildJwtResponse(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtUtil.generateAccessToken(userDetails);

        String refreshTokenValue = generateSecureToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenValue)
                .expiryDate(LocalDateTime.now().plusNanos(refreshTokenExpirationMs * 1_000_000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        List<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toList());

        return JwtResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .roles(roles)
                .mustChangePassword(user.getMustChangePassword())
                .build();
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
