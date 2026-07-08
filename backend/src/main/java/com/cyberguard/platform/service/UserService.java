package com.cyberguard.platform.service;

import com.cyberguard.platform.dto.request.CreateUserRequest;
import com.cyberguard.platform.dto.request.UpdateUserRequest;
import com.cyberguard.platform.dto.response.ResetPasswordResponse;
import com.cyberguard.platform.dto.response.UserResponse;
import com.cyberguard.platform.entity.NotificationPreference;
import com.cyberguard.platform.entity.Role;
import com.cyberguard.platform.entity.User;
import com.cyberguard.platform.entity.enums.NotificationType;
import com.cyberguard.platform.entity.enums.Severity;
import com.cyberguard.platform.entity.enums.UserStatus;
import com.cyberguard.platform.exception.BadRequestException;
import com.cyberguard.platform.exception.ResourceNotFoundException;
import com.cyberguard.platform.repository.NotificationPreferenceRepository;
import com.cyberguard.platform.repository.RoleRepository;
import com.cyberguard.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final List<String> ROLE_RANK = List.of(Role.USER, Role.ANALYST, Role.ADMIN);
    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public Page<User> search(String search, UserStatus status, String department, String role,
                              String sortBy, String sortDir, int page, int size) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : "%" + search.trim().toLowerCase() + "%";
        String normalizedDept = (department == null || department.isBlank()) ? null : department.trim();
        String normalizedRole = (role == null || role.isBlank()) ? null : role.trim();

        String sortField = switch (sortBy == null ? "" : sortBy) {
            case "fullName" -> "fullName";
            case "createdAt" -> "createdAt";
            default -> "username";
        };
        Sort sort = Sort.by("desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortField);
        Pageable pageable = PageRequest.of(page, size, sort);

        return userRepository.search(normalizedSearch, status, normalizedDept, normalizedRole, pageable);
    }

    public User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional
    public User createUser(CreateUserRequest request, User actor, String ip) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Password and confirm password do not match");
        }
        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRole()));

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
                .mustChangePassword(false)
                .roles(roles)
                .build();
        user = userRepository.save(user);

        notificationPreferenceRepository.save(NotificationPreference.builder().user(user).build());

        auditLogService.log(actor, "USER_CREATED", "User", user.getId(),
                "Created user '" + user.getUsername() + "' with role " + role.getName(), ip);
        notificationService.notifyAllAdmins("User Created",
                "Account '" + user.getUsername() + "' created with role " + role.getName() + " by " + actor.getUsername(),
                NotificationType.SYSTEM, Severity.LOW, "user-created");

        return user;
    }

    @Transactional
    public User updateUser(Long id, UpdateUserRequest request, User actor, String ip) {
        User user = getUserOrThrow(id);

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setDepartment(request.getDepartment());
        user.setPhone(request.getPhone());

        if (request.getStatus() != null) {
            applyStatusChange(user, request.getStatus(), actor, ip);
        }
        if (request.getRole() != null && !request.getRole().isBlank()) {
            applyRoleChange(user, Set.of(request.getRole()), actor, ip);
        }

        user = userRepository.save(user);
        auditLogService.log(actor, "USER_UPDATED", "User", user.getId(),
                "Updated profile fields for user '" + user.getUsername() + "'", ip);
        return user;
    }

    @Transactional
    public User updateStatus(Long id, UserStatus status, User actor, String ip) {
        User user = getUserOrThrow(id);
        applyStatusChange(user, status, actor, ip);
        return userRepository.save(user);
    }

    @Transactional
    public User updateRoles(Long id, Set<String> roleNames, User actor, String ip) {
        User user = getUserOrThrow(id);
        applyRoleChange(user, roleNames, actor, ip);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id, User actor, String ip) {
        if (id.equals(actor.getId())) {
            throw new BadRequestException("You cannot delete your own account while logged in as it.");
        }
        User target = getUserOrThrow(id);
        boolean targetIsAdmin = target.getRoles().stream().anyMatch(r -> r.getName().equals(Role.ADMIN));
        if (targetIsAdmin && userRepository.countByRoles_Name(Role.ADMIN) <= 1) {
            throw new BadRequestException("Cannot delete the last remaining administrator account.");
        }

        String targetUsername = target.getUsername();
        try {
            userRepository.delete(target);
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException(
                    "This user cannot be permanently deleted because they have existing incident or activity " +
                    "history tied to their account. Disable the account instead to preserve audit trail integrity.");
        }

        auditLogService.log(actor, "USER_DELETED", "User", id, "Deleted user '" + targetUsername + "'", ip);
    }

    @Transactional
    public ResetPasswordResponse resetPassword(Long id, User actor, String ip) {
        User user = getUserOrThrow(id);
        String tempPassword = generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);

        auditLogService.log(actor, "PASSWORD_RESET", "User", user.getId(),
                "Temporary password issued for user '" + user.getUsername() + "'", ip);
        notificationService.notifyAllAdmins("Password Reset",
                "Temporary password issued for '" + user.getUsername() + "' by " + actor.getUsername(),
                NotificationType.SYSTEM, Severity.MEDIUM, "password-reset");

        return ResetPasswordResponse.builder()
                .temporaryPassword(tempPassword)
                .message("Temporary password generated. The user must change it on next login.")
                .build();
    }

    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .department(user.getDepartment())
                .phone(user.getPhone())
                .status(user.getStatus())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                .lastLoginAt(user.getLastLoginAt())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .mustChangePassword(user.getMustChangePassword())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private void applyStatusChange(User user, UserStatus newStatus, User actor, String ip) {
        UserStatus oldStatus = user.getStatus();
        if (oldStatus == newStatus) {
            return;
        }
        user.setStatus(newStatus);
        String action = switch (newStatus) {
            case DISABLED -> "ACCOUNT_DISABLED";
            case LOCKED -> "ACCOUNT_LOCKED";
            case ACTIVE -> "ACCOUNT_ENABLED";
        };
        auditLogService.log(actor, action, "User", user.getId(),
                "Status changed from " + oldStatus + " to " + newStatus + " for user '" + user.getUsername() + "'", ip);
    }

    private void applyRoleChange(User user, Set<String> roleNames, User actor, String ip) {
        int oldRank = maxRank(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()));
        Set<Role> roles = roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + name)))
                .collect(Collectors.toCollection(HashSet::new));
        int newRank = maxRank(roleNames);

        if (oldRank == newRank && user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()).equals(roleNames)) {
            return;
        }

        user.setRoles(roles);
        String action = newRank > oldRank ? "USER_PROMOTED" : (newRank < oldRank ? "USER_DEMOTED" : "ROLE_CHANGED");
        auditLogService.log(actor, action, "User", user.getId(),
                "Role changed to " + String.join(",", roleNames) + " for user '" + user.getUsername() + "'", ip);
    }

    private int maxRank(Set<String> roleNames) {
        return roleNames.stream().mapToInt(ROLE_RANK::indexOf).max().orElse(0);
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(secureRandom.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
