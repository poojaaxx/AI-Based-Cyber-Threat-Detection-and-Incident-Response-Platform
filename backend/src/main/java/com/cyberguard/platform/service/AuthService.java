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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
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
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final AuditLogService auditLogService;
    private final AuthenticationMonitoringService authenticationMonitoringService;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    /** Maximum consecutive failures before the account is temporarily locked. */
    @Value("${app.auth.max-failed-attempts:5}")
    private int maxFailedAttempts;

    /** Minutes the account stays locked after breaching the max-failed-attempts threshold. */
    @Value("${app.auth.lockout-duration-minutes:15}")
    private int lockoutDurationMinutes;

    @jakarta.annotation.PostConstruct
    void validateLockoutConfiguration() {
        if (maxFailedAttempts < 1 || lockoutDurationMinutes < 1) {
            throw new IllegalStateException("Lockout threshold and duration must be positive");
        }
    }

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

    // Authentication rejection must commit the failure counter, lock, and attempt log.
    @Transactional(noRollbackFor = org.springframework.security.core.AuthenticationException.class)
    public JwtResponse login(LoginRequest request, String ipAddress, String userAgent) {
        java.time.Instant observedAt = java.time.Instant.now();
        // Serialize attempts for this account so concurrent failures cannot lose increments.
        User account = userRepository.findForLogin(request.getUsernameOrEmail()).orElse(null);
        try {
            if (account != null) checkAccountLock(account);
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsernameOrEmail(), request.getPassword())
            );
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            // Successful authentication clears the consecutive-failure state.
            // Expired temporary locks have already been cleared before authentication.
            user.setLastLoginAt(LocalDateTime.now());
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            if (user.getStatus() == UserStatus.LOCKED) {
                user.setStatus(UserStatus.ACTIVE);
            }
            userRepository.save(user);

            authenticationMonitoringService.record(LoginAttempt.builder()
                    .user(user).usernameAttempted(request.getUsernameOrEmail())
                    .ipAddress(ipAddress).success(true).userAgent(userAgent).build(),
                    "SUCCESS", observedAt, false, maxFailedAttempts);

            auditLogService.log(user, "LOGIN_SUCCESS", "User", user.getId(), "Successful login", ipAddress);

            return buildJwtResponse(user);
        } catch (LockedException ex) {
            recordAuthenticationOutcome(request, account, ipAddress, userAgent, "LOCKED", observedAt, false);
            throw ex;
        } catch (BadCredentialsException ex) {
            if (account != null) recordFailedAttempt(account, ipAddress);
            boolean crossed = account != null && account.getFailedLoginAttempts() == maxFailedAttempts;
            recordAuthenticationOutcome(request, account, ipAddress, userAgent, "FAILED", observedAt, crossed);
            throw ex;
        } catch (org.springframework.security.core.AuthenticationException ex) {
            recordAuthenticationOutcome(request, account, ipAddress, userAgent, "REJECTED", observedAt, false);
            throw ex;
        }
    }

    private void recordAuthenticationOutcome(LoginRequest request, User account, String ip, String agent,
                                             String outcome, java.time.Instant observedAt, boolean crossed) {
        authenticationMonitoringService.record(LoginAttempt.builder().user(account)
                .usernameAttempted(request.getUsernameOrEmail()).ipAddress(ip).success(false)
                .userAgent(agent).build(), outcome, observedAt, crossed, maxFailedAttempts);
    }

    /**
     * Checks whether the given user account is currently locked.
     * If locked but the lock window has expired, the account is automatically
     * unlocked so the AuthenticationManager can proceed with credential verification.
     *
     * @throws LockedException if the lockout period has not yet elapsed.
     */
    private void checkAccountLock(User user) {
        if (user.getLockedUntil() == null) {
            return; // Not locked.
        }
        if (!LocalDateTime.now().isBefore(user.getLockedUntil())) {
            // Reset expired temporary state before credential verification.
            if (user.getStatus() == UserStatus.LOCKED) user.setStatus(UserStatus.ACTIVE);
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
            log.info("Account lockout expired for user '{}'; automatically unlocked.", user.getUsername());
            return;
        }
        // Still within the lockout window.
        log.warn("Login rejected: account '{}' is temporarily locked until {}.", user.getUsername(), user.getLockedUntil());
        throw new LockedException(
                "Account temporarily locked due to too many failed attempts. "
                + "Please try again after " + lockoutDurationMinutes + " minutes.");
    }

    /**
     * Increments the failed-attempt counter. Locks the account if the configured
     * threshold is reached. The lock is temporary (expires after lockoutDurationMinutes).
     */
    private void recordFailedAttempt(User user, String ipAddress) {
        int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= maxFailedAttempts) {
            LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(lockoutDurationMinutes);
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(lockUntil);
            userRepository.save(user);
            log.warn("Account '{}' locked until {} after {} failed attempts from IP {}.",
                    user.getUsername(), lockUntil, attempts, ipAddress);
        } else {
            userRepository.save(user);
            log.debug("Failed login attempt {}/{} for user '{}' from IP {}.",
                    attempts, maxFailedAttempts, user.getUsername(), ipAddress);
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
        if (user.getStatus() != UserStatus.ACTIVE ||
                (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))) {
            throw new LockedException("Account unavailable; please log in again");
        }
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
