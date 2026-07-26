package com.biashara.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/** Request and response payloads for the authentication endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            String email,

            @NotBlank(message = "Password is required")
            String password) {
    }

    public record RefreshRequest(
            @NotBlank(message = "Refresh token is required")
            String refreshToken) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Your current password is required")
            String currentPassword,

            @NotBlank(message = "A new password is required")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String newPassword,

            @NotBlank(message = "Please confirm your new password")
            String confirmPassword) {
    }

    public record ForgotPasswordRequest(
            @NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String newPassword) {
    }

    /** The authenticated user, as the frontend needs them for nav and gating. */
    public record UserSummary(
            Long id,
            String email,
            String fullName,
            String initials,
            String position,
            String avatarUrl,
            Long tenantId,
            String tenantName,
            String currency,
            String department,
            String branch,
            Set<String> roles,
            String primaryRoleName,
            int hierarchyLevel,
            boolean platformAdmin,
            Set<String> permissions) {
    }

    public record LoginResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            /** Forces the client to the change-password screen before anything else. */
            boolean mustChangePassword,
            UserSummary user) {
    }

    /**
     * A seeded login, surfaced on the sign-in screen so a reviewer can enter the
     * system as any role without being handed credentials out of band.
     */
    public record DemoAccount(
            String email,
            String password,
            String roleCode,
            String roleName,
            String fullName,
            String position,
            String description,
            int permissionCount,
            List<String> canAccess) {
    }
}
