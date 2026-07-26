package com.biashara.iam.web;

import com.biashara.common.exception.NotFoundException;
import com.biashara.iam.dto.UserDtos;
import com.biashara.iam.repository.BranchRepository;
import com.biashara.iam.repository.DepartmentRepository;
import com.biashara.iam.repository.LoginHistoryRepository;
import com.biashara.iam.repository.PermissionRepository;
import com.biashara.iam.repository.RoleRepository;
import com.biashara.iam.repository.UserInvitationRepository;
import com.biashara.iam.repository.UserRepository;
import com.biashara.iam.security.CurrentUser;
import com.biashara.iam.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Management", description = "Users, roles, permissions, invitations and login history")
public class UserManagementController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final DepartmentRepository departmentRepository;
    private final BranchRepository branchRepository;
    private final UserInvitationRepository invitationRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final UserManagementService userManagementService;
    private final CurrentUser currentUser;

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('admin.user.view')")
    @Operation(summary = "List or search users")
    public Page<UserDtos.UserResponse> users(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Long tenantId = currentUser.tenantId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("firstName").ascending());

        return (search == null || search.isBlank()
                ? userRepository.findByTenantIdAndDeletedFalse(tenantId, pageable)
                : userRepository.search(tenantId, search.trim(), pageable))
                .map(UserDtos.UserResponse::from);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('admin.user.view')")
    @Operation(summary = "One user")
    public UserDtos.UserResponse user(@PathVariable Long id) {
        return userRepository.findByIdAndDeletedFalse(id)
                .filter(user -> user.getTenant() != null
                        && user.getTenant().getId().equals(currentUser.tenantId()))
                .map(UserDtos.UserResponse::from)
                .orElseThrow(() -> NotFoundException.of("User", id));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('admin.user.create')")
    @Operation(summary = "Create a user and issue an invitation with a temporary password")
    public UserDtos.CreatedUserResponse createUser(@Valid @RequestBody UserDtos.CreateUserRequest request) {
        return userManagementService.createUser(currentUser.require(), request);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('admin.user.update')")
    @Operation(summary = "Update a user, their role or their status")
    public UserDtos.UserResponse updateUser(@PathVariable Long id,
                                            @Valid @RequestBody UserDtos.UpdateUserRequest request) {
        return userManagementService.updateUser(currentUser.require(), id, request);
    }

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasAuthority('admin.user.reset_password')")
    @Operation(summary = "Issue a new temporary password and revoke all sessions")
    public UserDtos.PasswordResetResponse resetPassword(@PathVariable Long id) {
        return userManagementService.resetPassword(currentUser.require(), id);
    }

    @PostMapping("/users/{id}/unlock")
    @PreAuthorize("hasAuthority('admin.user.update')")
    @Operation(summary = "Clear a lockout after failed sign-in attempts")
    public UserDtos.UserResponse unlock(@PathVariable Long id) {
        return userManagementService.unlock(currentUser.require(), id);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('admin.user.delete')")
    @Operation(summary = "Deactivate a user (soft delete)")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        userManagementService.deleteUser(currentUser.require(), id);
        return Map.of("success", true, "message", "Account deactivated");
    }

    @GetMapping("/users/{id}/login-history")
    @PreAuthorize("hasAuthority('admin.audit.view')")
    @Operation(summary = "Recent sign-in attempts for one user")
    public List<UserDtos.LoginHistoryResponse> loginHistory(@PathVariable Long id) {
        return loginHistoryRepository.findTop20ByUserIdOrderByOccurredAtDesc(id).stream()
                .map(UserDtos.LoginHistoryResponse::from)
                .toList();
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('admin.role.view')")
    @Operation(summary = "Roles available to this business")
    public List<UserDtos.RoleResponse> roles() {
        return roleRepository.findAvailableToTenant(currentUser.tenantId()).stream()
                .map(UserDtos.RoleResponse::from)
                .toList();
    }

    /**
     * Only the roles the caller is actually allowed to hand out — strictly less
     * privileged than their own. The create-user form is driven by this, so the
     * hierarchy rule is visible rather than only enforced on submit.
     */
    @GetMapping("/roles/assignable")
    @PreAuthorize("hasAuthority('admin.user.create')")
    @Operation(summary = "Roles this user may assign")
    public List<UserDtos.RoleResponse> assignableRoles() {
        var principal = currentUser.require();
        return roleRepository.findAssignableBy(principal.getTenantId(), principal.getHierarchyLevel())
                .stream()
                .map(UserDtos.RoleResponse::from)
                .toList();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('admin.role.view')")
    @Operation(summary = "The full permission catalogue, grouped by module")
    public Map<String, List<UserDtos.PermissionResponse>> permissions() {
        return permissionRepository.findAllByOrderByModuleAscCodeAsc().stream()
                .map(UserDtos.PermissionResponse::from)
                .collect(java.util.stream.Collectors.groupingBy(
                        UserDtos.PermissionResponse::module,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
    }

    @GetMapping("/invitations")
    @PreAuthorize("hasAuthority('admin.user.view')")
    @Operation(summary = "Account invitations, including the rendered email body")
    public List<UserDtos.InvitationResponse> invitations() {
        return invitationRepository
                .findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(currentUser.tenantId()).stream()
                .map(UserDtos.InvitationResponse::from)
                .toList();
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('admin.user.view')")
    @Operation(summary = "Departments")
    public List<Map<String, Object>> departments() {
        return departmentRepository
                .findByTenantIdAndDeletedFalseOrderByNameAsc(currentUser.tenantId()).stream()
                .map(department -> Map.<String, Object>of(
                        "id", department.getId(),
                        "name", department.getName(),
                        "code", department.getCode(),
                        "head", department.getHead() == null
                                ? "Unassigned" : department.getHead().getFullName()))
                .toList();
    }

    @GetMapping("/branches")
    @PreAuthorize("hasAuthority('admin.user.view')")
    @Operation(summary = "Branches")
    public List<Map<String, Object>> branches() {
        return branchRepository
                .findByTenantIdAndDeletedFalseOrderByNameAsc(currentUser.tenantId()).stream()
                .map(branch -> Map.<String, Object>of(
                        "id", branch.getId(),
                        "name", branch.getName(),
                        "code", branch.getCode(),
                        "city", branch.getCity() == null ? "" : branch.getCity(),
                        "mainBranch", branch.isMainBranch()))
                .toList();
    }
}
