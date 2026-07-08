package com.cyberguard.platform.service;

import com.cyberguard.platform.dto.request.ChangePasswordRequest;
import com.cyberguard.platform.dto.request.NotificationPreferenceRequest;
import com.cyberguard.platform.dto.request.UpdateProfileRequest;
import com.cyberguard.platform.entity.ApiConfiguration;
import com.cyberguard.platform.entity.NotificationPreference;
import com.cyberguard.platform.entity.User;
import com.cyberguard.platform.exception.BadRequestException;
import com.cyberguard.platform.exception.ResourceNotFoundException;
import com.cyberguard.platform.repository.ApiConfigurationRepository;
import com.cyberguard.platform.repository.NotificationPreferenceRepository;
import com.cyberguard.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final UserRepository userRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final ApiConfigurationRepository apiConfigurationRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getDepartment() != null) user.setDepartment(request.getDepartment());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    @Transactional
    public NotificationPreference updateNotificationPreferences(Long userId, NotificationPreferenceRequest request) {
        NotificationPreference prefs = notificationPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> NotificationPreference.builder()
                        .user(userRepository.getReferenceById(userId)).build());

        if (request.getEmailAlerts() != null) prefs.setEmailAlerts(request.getEmailAlerts());
        if (request.getCriticalAlerts() != null) prefs.setCriticalAlerts(request.getCriticalAlerts());
        if (request.getDashboardAlerts() != null) prefs.setDashboardAlerts(request.getDashboardAlerts());
        if (request.getWeeklySummary() != null) prefs.setWeeklySummary(request.getWeeklySummary());

        return notificationPreferenceRepository.save(prefs);
    }

    public NotificationPreference getNotificationPreferences(Long userId) {
        return notificationPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> NotificationPreference.builder().user(userRepository.getReferenceById(userId)).build());
    }

    @Transactional
    public ApiConfiguration regenerateApiKey(Long userId) {
        ApiConfiguration config = apiConfigurationRepository.findByUserId(userId)
                .orElseGet(() -> ApiConfiguration.builder().user(userRepository.getReferenceById(userId)).build());
        config.setApiKey(generateApiKey());
        return apiConfigurationRepository.save(config);
    }

    public ApiConfiguration getApiConfiguration(Long userId) {
        return apiConfigurationRepository.findByUserId(userId).orElse(null);
    }

    private String generateApiKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "cg_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
