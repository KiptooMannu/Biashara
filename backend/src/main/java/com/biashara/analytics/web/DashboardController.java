package com.biashara.analytics.web;

import com.biashara.analytics.dto.AnalyticsDtos;
import com.biashara.analytics.service.BusinessHealthService;
import com.biashara.analytics.service.DashboardService;
import com.biashara.iam.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard", description = "Executive command centre and business health")
public class DashboardController {

    private final DashboardService dashboardService;
    private final BusinessHealthService healthService;
    private final CurrentUser currentUser;

    @GetMapping
    @PreAuthorize("hasAuthority('dashboard.view')")
    @Operation(summary = "Everything the dashboard needs, in one request")
    public AnalyticsDtos.DashboardResponse dashboard() {
        var principal = currentUser.require();
        return dashboardService.build(
                currentUser.tenantId(),
                principal.getId(),
                List.copyOf(principal.getPermissionCodes()));
    }

    @GetMapping("/health")
    @PreAuthorize("hasAuthority('dashboard.view')")
    @Operation(summary = "Business health score with its weighted components")
    public AnalyticsDtos.BusinessHealth health() {
        return healthService.calculate(currentUser.tenantId());
    }
}
