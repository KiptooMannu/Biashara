package com.biashara.asset.web;

import com.biashara.asset.repository.AssetRepository;
import com.biashara.common.enums.ProjectStatus;
import com.biashara.common.enums.TaskStatus;
import com.biashara.iam.security.CurrentUser;
import com.biashara.project.repository.ProjectRepository;
import com.biashara.project.repository.ProjectTaskRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Assets and projects. Both are read-heavy in the demo, so they share a
 * controller rather than warranting one each.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Assets & Projects", description = "Asset register, depreciation, projects and tasks")
public class AssetProjectController {

    private final AssetRepository assetRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTaskRepository taskRepository;
    private final CurrentUser currentUser;

    @GetMapping("/assets")
    @PreAuthorize("hasAuthority('asset.view')")
    @Operation(summary = "The asset register, with book value computed per asset")
    public List<Map<String, Object>> assets() {
        return assetRepository.findByTenantIdAndDeletedFalseOrderByNameAsc(currentUser.tenantId()).stream()
                .map(asset -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", asset.getId());
                    row.put("assetTag", asset.getAssetTag());
                    row.put("name", asset.getName());
                    row.put("category", asset.getCategory());
                    row.put("serialNumber", asset.getSerialNumber());
                    row.put("purchaseDate", asset.getPurchaseDate());
                    row.put("purchaseCost", asset.getPurchaseCost());
                    row.put("depreciationRate", asset.getDepreciationRate());
                    // Straight-line book value as at today, floored at salvage.
                    row.put("currentValue", asset.getCurrentValue());
                    row.put("status", asset.getStatus().name());
                    row.put("location", asset.getLocation());
                    row.put("assignedTo", asset.getAssignedTo() == null
                            ? null : asset.getAssignedTo().getFullName());
                    row.put("branch", asset.getBranch() == null ? null : asset.getBranch().getName());
                    row.put("warrantyExpiry", asset.getWarrantyExpiry());
                    row.put("underWarranty", asset.isUnderWarranty());
                    row.put("nextServiceDate", asset.getNextServiceDate());
                    row.put("serviceDue", asset.getNextServiceDate() != null
                            && asset.getNextServiceDate().isBefore(LocalDate.now()));
                    return row;
                })
                .toList();
    }

    @GetMapping("/assets/summary")
    @PreAuthorize("hasAuthority('asset.view')")
    @Operation(summary = "Asset register headline figures")
    public Map<String, Object> assetSummary() {
        Long tenantId = currentUser.tenantId();
        var assets = assetRepository.findByTenantIdAndDeletedFalseOrderByNameAsc(tenantId);

        var bookValue = assets.stream()
                .map(asset -> asset.getCurrentValue())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        return Map.of(
                "totalAssets", assetRepository.countByTenantIdAndDeletedFalse(tenantId),
                "purchaseCost", assetRepository.totalPurchaseCost(tenantId),
                "bookValue", bookValue,
                "serviceDue", assetRepository
                        .findByTenantIdAndNextServiceDateBeforeAndDeletedFalseOrderByNextServiceDateAsc(
                                tenantId, LocalDate.now()).size(),
                "byCategory", assetRepository.valueByCategory(tenantId).stream()
                        .map(value -> Map.<String, Object>of(
                                "label", value.getLabel() == null ? "Uncategorised" : value.getLabel(),
                                "value", value.getValue(),
                                "count", value.getCount()))
                        .toList());
    }

    @GetMapping("/projects")
    @PreAuthorize("hasAuthority('project.view')")
    @Operation(summary = "Projects with budget and profitability")
    public List<Map<String, Object>> projects() {
        return projectRepository.findByTenantIdAndDeletedFalseOrderByStartDateDesc(currentUser.tenantId())
                .stream()
                .map(project -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", project.getId());
                    row.put("code", project.getCode());
                    row.put("name", project.getName());
                    row.put("client", project.getClient() == null ? null : project.getClient().getName());
                    row.put("manager", project.getManager() == null ? null : project.getManager().getFullName());
                    row.put("startDate", project.getStartDate());
                    row.put("endDate", project.getEndDate());
                    row.put("budget", project.getBudget());
                    row.put("actualCost", project.getActualCost());
                    row.put("contractValue", project.getContractValue());
                    row.put("amountInvoiced", project.getAmountInvoiced());
                    row.put("profit", project.getProfit());
                    row.put("overBudget", project.isOverBudget());
                    row.put("status", project.getStatus().name());
                    row.put("progress", project.getProgress());
                    return row;
                })
                .toList();
    }

    /** Tasks grouped into Kanban columns. */
    @GetMapping("/projects/board")
    @PreAuthorize("hasAuthority('project.view')")
    @Operation(summary = "Task board, grouped by status")
    public Map<String, List<Map<String, Object>>> board() {
        Long tenantId = currentUser.tenantId();
        Map<String, List<Map<String, Object>>> board = new java.util.LinkedHashMap<>();

        for (TaskStatus status : TaskStatus.values()) {
            board.put(status.name(), taskRepository
                    .findByTenantIdAndStatusAndDeletedFalseOrderByBoardPositionAsc(tenantId, status)
                    .stream()
                    .map(task -> {
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("id", task.getId());
                        row.put("title", task.getTitle());
                        row.put("project", task.getProject() == null ? null : task.getProject().getName());
                        row.put("assignee", task.getAssignee() == null
                                ? null : task.getAssignee().getFullName());
                        row.put("priority", task.getPriority() == null ? null : task.getPriority().name());
                        row.put("dueDate", task.getDueDate());
                        row.put("overdue", task.isOverdue());
                        row.put("estimatedHours", task.getEstimatedHours());
                        row.put("actualHours", task.getActualHours());
                        return row;
                    })
                    .toList());
        }
        return board;
    }

    @GetMapping("/projects/summary")
    @PreAuthorize("hasAuthority('project.view')")
    @Operation(summary = "Project headline figures")
    public Map<String, Object> projectSummary() {
        Long tenantId = currentUser.tenantId();
        return Map.of(
                "totalProjects", projectRepository.countByTenantIdAndDeletedFalse(tenantId),
                "inProgress", projectRepository.countByTenantIdAndStatusAndDeletedFalse(
                        tenantId, ProjectStatus.IN_PROGRESS),
                "completed", projectRepository.countByTenantIdAndStatusAndDeletedFalse(
                        tenantId, ProjectStatus.COMPLETED),
                "totalTasks", taskRepository.countByTenantIdAndDeletedFalse(tenantId),
                "openTasks", taskRepository.countByTenantIdAndStatusAndDeletedFalse(tenantId, TaskStatus.TODO)
                        + taskRepository.countByTenantIdAndStatusAndDeletedFalse(
                        tenantId, TaskStatus.IN_PROGRESS));
    }
}
