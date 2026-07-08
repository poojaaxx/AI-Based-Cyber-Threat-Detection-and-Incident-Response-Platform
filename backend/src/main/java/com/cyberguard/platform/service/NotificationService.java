package com.cyberguard.platform.service;

import com.cyberguard.platform.dto.response.NotificationPayload;
import com.cyberguard.platform.entity.Notification;
import com.cyberguard.platform.entity.Role;
import com.cyberguard.platform.entity.User;
import com.cyberguard.platform.entity.enums.NotificationType;
import com.cyberguard.platform.entity.enums.Severity;
import com.cyberguard.platform.exception.ResourceNotFoundException;
import com.cyberguard.platform.repository.NotificationRepository;
import com.cyberguard.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SseHubService sseHubService;

    public void notifyUser(User user, String title, String message, NotificationType type) {
        notifyUser(user, title, message, type, null, defaultIconFor(type));
    }

    /** Richer variant carrying severity (for color coding) and a semantic icon key for the live notification center. */
    public void notifyUser(User user, String title, String message, NotificationType type, Severity severity, String icon) {
        Notification notification = Notification.builder()
                .user(user).title(title).message(message).type(type).severity(severity).icon(icon).build();
        notification = notificationRepository.save(notification);

        sseHubService.pushNotification(user.getId(), NotificationPayload.from(notification));

        if (type == NotificationType.CRITICAL) {
            emailService.sendCriticalAlertEmail(user.getEmail(), title, message);
        }
    }

    public void notifyAllAdmins(String title, String message, NotificationType type) {
        notifyAllAdmins(title, message, type, null, defaultIconFor(type));
    }

    public void notifyAllAdmins(String title, String message, NotificationType type, Severity severity, String icon) {
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName().equals(Role.ADMIN)))
                .toList();
        admins.forEach(admin -> notifyUser(admin, title, message, type, severity, icon));
    }

    public Page<Notification> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
    }

    private String defaultIconFor(NotificationType type) {
        return switch (type) {
            case THREAT -> "threat";
            case CRITICAL -> "critical";
            case INCIDENT -> "incident";
            case SYSTEM -> "system";
        };
    }
}
