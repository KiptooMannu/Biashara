package com.biashara.iam.dto;

import com.biashara.iam.domain.AuditLog;
import com.biashara.iam.domain.LoginHistory;
import com.biashara.iam.domain.Permission;
import com.biashara.iam.domain.Role;
import com.biashara.iam.domain.User;
import com.biashara.iam.domain.UserInvitation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(
            Long id,
            String email,
            String username,
            String firstName,
            String lastName,
            String fullName,
            String initials,
            String phone,
            String employeeNumber,
            String position,
            String department,
            Long departmentId,
            String branch,
            Long branchId,
            String manager,
            LocalDate employmentDate,
            Set<String> roles,
            String primaryRoleName,
            int hierarchyLevel,
            int permissionCount,
            String status,
            boolean firstLogin,
            boolean locked,
            boolean twoFactorEnabled,
            boolean platformAdmin,
            int failedLoginAttempts,
            LocalDateTime lastLoginAt,
            String lastLoginIp,
            LocalDateTime passwordChangedAt) {

        public static UserResponse from(User user) {
            Role primary = user.getRoles().stream()
                    .min(java.util.Comparator.comparing(Role::getHierarchyLevel))
                    .orElse(null);

            return new UserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getUsername(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getFullName(),
                    user.getInitials(),
                    user.getPhone(),
                    user.getEmployeeNumber(),
                    user.getPosition(),
                    user.getDepartment() == null ? null : user.getDepartment().getName(),
                    user.getDepartment() == null ? null : user.getDepartment().getId(),
                    user.getBranch() == null ? null : user.getBranch().getName(),
                    user.getBranch() == null ? null : user.getBranch().getId(),
                    user.getManager() == null ? null : user.getManager().getFullName(),
                    user.getEmploymentDate(),
                    user.collectRoleCodes(),
                    primary == null ? null : primary.getName(),
                    user.highestHierarchyLevel(),
                    user.collectPermissionCodes().size(),
                    user.getStatus().name(),
                    user.isFirstLogin(),
                    user.isLocked(),
                    user.isTwoFactorEnabled(),
                    user.isPlatformAdmin(),
                    user.getFailedLoginAttempts(),
                    user.getLastLoginAt(),
                    user.getLastLoginIp(),
                    user.getPasswordChangedAt());
        }
    }

    /**
     * Creating a user. There is deliberately no password field — the system
     * generates a temporary one, so an administrator never chooses another
     * person's password or learns it beyond the one-time invitation.
     */
    public record CreateUserRequest(
            @NotBlank(message = "First name is required") String firstName,
            @NotBlank(message = "Last name is required") String lastName,
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address") String email,
            String phone,
            String nationalId,
            String employeeNumber,
            String position,
            Long departmentId,
            Long branchId,
            Long managerId,
            LocalDate employmentDate,
            @NotNull(message = "Choose a role") Long roleId,
            /** Optional one-off grants on top of the role. */
            List<Long> extraPermissionIds) {
    }

    public record UpdateUserRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            String phone,
            String position,
            Long departmentId,
            Long branchId,
            Long managerId,
            Long roleId,
            String status) {
    }

    /**
     * The result of creating a user, including the generated temporary password.
     *
     * Returned exactly once, at creation. In production this goes to the
     * notification service to be emailed; with no SMTP in the demo it is surfaced
     * here and on the invitation record so the flow stays testable.
     */
    public record CreatedUserResponse(
            UserResponse user,
            String temporaryPassword,
            String invitationToken,
            String emailPreview,
            String message) {
    }

    public record RoleResponse(
            Long id,
            String code,
            String name,
            String description,
            int hierarchyLevel,
            String department,
            boolean systemRole,
            int permissionCount,
            Set<String> permissions) {

        public static RoleResponse from(Role role) {
            return new RoleResponse(
                    role.getId(),
                    role.getCode(),
                    role.getName(),
                    role.getDescription(),
                    role.getHierarchyLevel(),
                    role.getDepartment() == null ? null : role.getDepartment().getName(),
                    role.isSystemRole(),
                    role.getPermissions().size(),
                    role.getPermissions().stream().map(Permission::getCode)
                            .collect(java.util.stream.Collectors.toSet()));
        }
    }

    public record PermissionResponse(Long id, String code, String module, String description) {

        public static PermissionResponse from(Permission permission) {
            return new PermissionResponse(
                    permission.getId(),
                    permission.getCode(),
                    permission.getModule(),
                    permission.getDescription());
        }
    }

    public record InvitationResponse(
            Long id,
            Long userId,
            String userName,
            String email,
            String status,
            String temporaryPassword,
            String invitedBy,
            LocalDateTime expiresAt,
            LocalDateTime acceptedAt,
            boolean expired,
            String emailBody) {

        public static InvitationResponse from(UserInvitation invitation) {
            return new InvitationResponse(
                    invitation.getId(),
                    invitation.getUser().getId(),
                    invitation.getUser().getFullName(),
                    invitation.getUser().getEmail(),
                    invitation.getStatus().name(),
                    invitation.getTemporaryPassword(),
                    invitation.getInvitedBy() == null ? null : invitation.getInvitedBy().getFullName(),
                    invitation.getExpiresAt(),
                    invitation.getAcceptedAt(),
                    invitation.isExpired(),
                    invitation.getEmailBody());
        }
    }

    public record AuditLogResponse(
            Long id,
            String actorName,
            String actorRole,
            String action,
            String module,
            String entityType,
            Long entityId,
            String targetName,
            String details,
            String ipAddress,
            LocalDateTime occurredAt) {

        public static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(
                    log.getId(),
                    log.getActorName(),
                    log.getActorRole(),
                    log.getAction(),
                    log.getModule(),
                    log.getEntityType(),
                    log.getEntityId(),
                    log.getTargetName(),
                    log.getDetails(),
                    log.getIpAddress(),
                    log.getOccurredAt());
        }
    }

    public record LoginHistoryResponse(
            Long id,
            String userName,
            String attemptedEmail,
            String status,
            String ipAddress,
            String device,
            String failureReason,
            LocalDateTime occurredAt) {

        public static LoginHistoryResponse from(LoginHistory history) {
            return new LoginHistoryResponse(
                    history.getId(),
                    history.getUser() == null ? null : history.getUser().getFullName(),
                    history.getAttemptedEmail(),
                    history.getStatus().name(),
                    history.getIpAddress(),
                    history.getDevice(),
                    history.getFailureReason(),
                    history.getOccurredAt());
        }
    }

    /** Reset outcome; the new temporary password is shown once. */
    public record PasswordResetResponse(String temporaryPassword, String message) {
    }
}
