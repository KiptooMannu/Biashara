package com.biashara.iam.web;

import com.biashara.iam.dto.UserDtos;
import com.biashara.iam.repository.AuditLogRepository;
import com.biashara.iam.repository.LoginHistoryRepository;
import com.biashara.iam.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Audit", description = "Audit trail and sign-in history")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final CurrentUser currentUser;

    @GetMapping
    @PreAuthorize("hasAuthority('admin.audit.view')")
    @Operation(summary = "The audit trail, newest first")
    public Page<UserDtos.AuditLogResponse> auditTrail(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        Long tenantId = currentUser.tenantId();
        var pageable = PageRequest.of(page, Math.min(size, 200));

        if (search != null && !search.isBlank()) {
            return auditLogRepository.search(tenantId, search.trim(), pageable)
                    .map(UserDtos.AuditLogResponse::from);
        }
        if (module != null && !module.isBlank()) {
            return auditLogRepository
                    .findByTenantIdAndModuleOrderByOccurredAtDesc(tenantId, module, pageable)
                    .map(UserDtos.AuditLogResponse::from);
        }
        return auditLogRepository.findByTenantIdOrderByOccurredAtDesc(tenantId, pageable)
                .map(UserDtos.AuditLogResponse::from);
    }

    @GetMapping("/login-history")
    @PreAuthorize("hasAuthority('admin.audit.view')")
    @Operation(summary = "Sign-in attempts across the business, successful and failed")
    public Page<UserDtos.LoginHistoryResponse> loginHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return loginHistoryRepository
                .findByTenantIdOrderByOccurredAtDesc(currentUser.tenantId(),
                        PageRequest.of(page, Math.min(size, 200)))
                .map(UserDtos.LoginHistoryResponse::from);
    }
}
