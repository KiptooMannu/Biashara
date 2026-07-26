package com.biashara.notification.web;

import com.biashara.iam.security.CurrentUser;
import com.biashara.notification.dto.NotificationDtos;
import com.biashara.notification.repository.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications", description = "In-app notification inbox")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final CurrentUser currentUser;

    /**
     * The caller's inbox: messages addressed to them plus business-wide broadcasts
     * they hold the permission to see. A cashier does not get payroll alerts.
     */
    @GetMapping
    @Operation(summary = "Notifications for the signed-in user")
    public List<NotificationDtos.NotificationResponse> inbox(
            @RequestParam(defaultValue = "20") int size) {
        var principal = currentUser.require();
        return notificationRepository
                .findInbox(principal.getTenantId(), principal.getId(),
                        PageRequest.of(0, Math.min(size, 100)))
                .stream()
                .filter(notification -> notification.getRequiredPermission() == null
                        || principal.getPermissionCodes().contains(notification.getRequiredPermission()))
                .map(NotificationDtos.NotificationResponse::from)
                .toList();
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread count for the bell badge")
    public Map<String, Object> unreadCount() {
        var principal = currentUser.require();
        return Map.of("count",
                notificationRepository.countUnread(principal.getTenantId(), principal.getId()));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark everything in the inbox as read")
    @Transactional
    public Map<String, Object> readAll() {
        var principal = currentUser.require();
        int updated = notificationRepository.markAllRead(principal.getTenantId(), principal.getId());
        return Map.of("success", true, "updated", updated);
    }
}
