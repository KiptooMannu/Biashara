package com.biashara.notification.dto;

import com.biashara.notification.domain.Notification;

import java.time.LocalDateTime;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            Long id,
            String title,
            String message,
            String severity,
            String channel,
            String module,
            String actionUrl,
            String icon,
            boolean read,
            LocalDateTime createdOn) {

        public static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getSeverity().name(),
                    notification.getChannel().name(),
                    notification.getModule(),
                    notification.getActionUrl(),
                    notification.getIcon(),
                    notification.isRead(),
                    notification.getCreatedOn());
        }
    }
}
