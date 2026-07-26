package com.biashara.iam.service;

import com.biashara.iam.domain.AuditLog;
import com.biashara.iam.domain.Tenant;
import com.biashara.iam.domain.User;
import com.biashara.iam.repository.AuditLogRepository;
import com.biashara.iam.security.CurrentUser;
import com.biashara.iam.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Writes the audit trail.
 *
 * Each entry is written in its own transaction ({@code REQUIRES_NEW}) so that a
 * failure in the operation being audited cannot roll the audit record away with
 * it — a rejected action is exactly the kind of thing you need a record of.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final CurrentUser currentUser;
    private final HttpServletRequest request;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String module, String entityType, Long entityId,
                       String targetName, String details) {
        UserPrincipal principal = currentUser.find().orElse(null);

        AuditLog entry = AuditLog.builder()
                .actorName(principal == null ? "system" : principal.getFullName())
                .actorRole(principal == null ? "SYSTEM" : String.join(", ", principal.getRoleCodes()))
                .action(action)
                .module(module)
                .entityType(entityType)
                .entityId(entityId)
                .targetName(targetName)
                .details(details)
                .ipAddress(clientIp())
                .userAgent(header("User-Agent"))
                .occurredAt(LocalDateTime.now())
                .build();

        if (principal != null) {
            entry.setActor(reference(User.class, principal.getId()));
            if (principal.getTenantId() != null) {
                entry.setTenant(reference(Tenant.class, principal.getTenantId()));
            }
        }
        auditLogRepository.save(entry);
    }

    /**
     * Used by flows that already hold the acting user, including the seeder.
     *
     * Joins the caller's transaction rather than starting its own: the actor may
     * have been created in that same uncommitted transaction, and a separate one
     * could not see it to satisfy the foreign key.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordAs(User actor, Tenant tenant, String action, String module,
                         String entityType, Long entityId, String targetName, String details) {
        auditLogRepository.save(AuditLog.builder()
                .actor(actor)
                .tenant(tenant)
                .actorName(actor == null ? "system" : actor.getFullName())
                .actorRole(actor == null ? "SYSTEM" : String.join(", ", actor.collectRoleCodes()))
                .action(action)
                .module(module)
                .entityType(entityType)
                .entityId(entityId)
                .targetName(targetName)
                .details(details)
                .ipAddress("127.0.0.1")
                .occurredAt(LocalDateTime.now())
                .build());
    }

    /**
     * Builds a reference without loading the row. Avoids a select just to satisfy
     * a foreign key on a write-only path.
     */
    @SuppressWarnings("unchecked")
    private <T> T reference(Class<T> type, Long id) {
        if (type == User.class) {
            User user = new User();
            user.setId(id);
            return (T) user;
        }
        Tenant tenant = new Tenant();
        tenant.setId(id);
        return (T) tenant;
    }

    private String clientIp() {
        String forwarded = header("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // First hop is the original client when behind a proxy.
            return forwarded.split(",")[0].trim();
        }
        try {
            return request.getRemoteAddr();
        } catch (RuntimeException outsideRequest) {
            return null;
        }
    }

    private String header(String name) {
        try {
            return request.getHeader(name);
        } catch (RuntimeException outsideRequest) {
            return null;
        }
    }
}
