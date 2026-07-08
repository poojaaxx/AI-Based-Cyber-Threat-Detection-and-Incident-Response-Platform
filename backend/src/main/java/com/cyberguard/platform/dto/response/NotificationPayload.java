package com.cyberguard.platform.dto.response;

import com.cyberguard.platform.entity.Notification;
import com.cyberguard.platform.entity.enums.NotificationType;
import com.cyberguard.platform.entity.enums.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Flat, lazy-association-free projection of a Notification, safe to push over SSE or return from the API. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPayload {
    private Long id;
    private String title;
    private String message;
    private NotificationType type;
    private Severity severity;
    private String icon;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationPayload from(Notification notification) {
        return NotificationPayload.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .severity(notification.getSeverity())
                .icon(notification.getIcon())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
