package com.biashara.auth.service;

import com.biashara.auth.dto.AuthDtos;
import com.biashara.common.enums.LoginStatus;
import com.biashara.common.enums.UserStatus;
import com.biashara.common.exception.BusinessRuleException;
import com.biashara.common.exception.UnauthorisedException;
import com.biashara.iam.domain.LoginHistory;
import com.biashara.iam.domain.RefreshToken;
import com.biashara.iam.domain.Role;
import com.biashara.iam.domain.User;
import com.biashara.iam.domain.UserInvitation;
import com.biashara.iam.repository.LoginHistoryRepository;
import com.biashara.iam.repository.RefreshTokenRepository;
import com.biashara.iam.repository.UserInvitationRepository;
import com.biashara.iam.repository.UserRepository;
import com.biashara.iam.security.JwtService;
import com.biashara.iam.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

/**
 * Login, refresh, logout and password change.
 *
 * There is no registration endpoint by design: the founding Business Owner is
 * seeded with the tenant, and every subsequent account is created by an authorised
 * administrator through {@code UserManagementService}.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final UserInvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final HttpServletRequest request;

    @Value("${biashara.security.max-failed-logins}")
    private int maxFailedLogins;

    @Value("${biashara.security.lockout-minutes}")
    private int lockoutMinutes;

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest credentials) {
        String email = credentials.email().trim();

        Optional<User> found = userRepository.findByEmailIgnoreCaseAndDeletedFalse(email);
        if (found.isEmpty()) {
            // Recorded even with no matching user, so credential stuffing is visible.
            recordLogin(null, email, LoginStatus.FAILED_BAD_CREDENTIALS, "No account for this email");
            throw new UnauthorisedException("Incorrect email or password");
        }

        User user = found.get();

        if (user.isLocked()) {
            recordLogin(user, email, LoginStatus.FAILED_ACCOUNT_LOCKED, "Account is locked out");
            throw new UnauthorisedException(
                    "This account is locked after too many failed attempts. Try again in a few minutes.");
        }

        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.INACTIVE) {
            recordLogin(user, email, LoginStatus.FAILED_ACCOUNT_DISABLED, "Account status " + user.getStatus());
            throw new UnauthorisedException("This account is not active. Contact your administrator.");
        }

        if (!passwordEncoder.matches(credentials.password(), user.getPasswordHash())) {
            registerFailure(user);
            recordLogin(user, email, LoginStatus.FAILED_BAD_CREDENTIALS, "Incorrect password");
            throw new UnauthorisedException("Incorrect email or password");
        }

        // Success: clear the failure counter and stamp the session.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(clientIp());
        if (user.getStatus() == UserStatus.PENDING_INVITATION && !user.isFirstLogin()) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        recordLogin(user, email, LoginStatus.SUCCESS, null);
        auditService.recordAs(user, user.getTenant(), "LOGIN", "Authentication",
                "User", user.getId(), user.getFullName(), "Signed in successfully");

        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.LoginResponse refresh(String refreshTokenValue) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new UnauthorisedException("This session is no longer valid. Please sign in again."));

        if (!stored.isUsable()) {
            throw new UnauthorisedException("This session has expired. Please sign in again.");
        }

        User user = userRepository.findByIdAndDeletedFalse(stored.getUser().getId())
                .orElseThrow(() -> new UnauthorisedException("Account no longer exists"));

        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.INACTIVE) {
            throw new UnauthorisedException("This account is not active");
        }

        // Rotate: the presented token is retired as a new one is issued, so a
        // stolen refresh token is usable at most once.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshTokenValue, Long userId) {
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
        userRepository.findByIdAndDeletedFalse(userId).ifPresent(user -> {
            recordLogin(user, user.getEmail(), LoginStatus.LOGOUT, null);
            auditService.recordAs(user, user.getTenant(), "LOGOUT", "Authentication",
                    "User", user.getId(), user.getFullName(), "Signed out");
        });
    }

    /**
     * Changes the caller's own password and clears the first-login flag. Every
     * other session is revoked, since a password change is often a response to
     * suspecting one is compromised.
     */
    @Transactional
    public void changePassword(Long userId, AuthDtos.ChangePasswordRequest changeRequest) {
        if (!changeRequest.newPassword().equals(changeRequest.confirmPassword())) {
            throw new BusinessRuleException("The new password and confirmation do not match");
        }

        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new UnauthorisedException("Account no longer exists"));

        if (!passwordEncoder.matches(changeRequest.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Your current password is incorrect");
        }
        if (passwordEncoder.matches(changeRequest.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Your new password must be different from the current one");
        }
        validatePasswordPolicy(changeRequest.newPassword());

        user.setPasswordHash(passwordEncoder.encode(changeRequest.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setFirstLogin(false);
        if (user.getStatus() == UserStatus.PENDING_INVITATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        // The temporary password is no longer needed and should not linger.
        invitationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).ifPresent(invitation -> {
            invitation.setTemporaryPassword(null);
            invitation.setStatus(com.biashara.common.enums.ApprovalStatus.APPROVED);
            invitation.setAcceptedAt(LocalDateTime.now());
            invitationRepository.save(invitation);
        });

        refreshTokenRepository.revokeAllForUser(userId);

        auditService.recordAs(user, user.getTenant(), "CHANGE_PASSWORD", "Authentication",
                "User", user.getId(), user.getFullName(), "Password changed by the account owner");
    }

    /**
     * The password policy from the specification: at least 8 characters with
     * upper case, lower case, a digit and a symbol.
     */
    public void validatePasswordPolicy(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessRuleException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new BusinessRuleException("Password must contain an upper case letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new BusinessRuleException("Password must contain a lower case letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new BusinessRuleException("Password must contain a number");
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            throw new BusinessRuleException("Password must contain a special character");
        }
    }

    // --- Internals ----------------------------------------------------------

    private AuthDtos.LoginResponse issueTokens(User user) {
        String accessToken = jwtService.issueAccessToken(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(jwtService.generateRefreshToken())
                .expiresAt(LocalDateTime.ofInstant(jwtService.refreshTokenExpiry(),
                        java.time.ZoneId.systemDefault()))
                .ipAddress(clientIp())
                .device(header("User-Agent"))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthDtos.LoginResponse(
                accessToken,
                refreshToken.getToken(),
                "Bearer",
                jwtService.accessTokenSeconds(),
                user.isFirstLogin(),
                toSummary(user));
    }

    public AuthDtos.UserSummary toSummary(User user) {
        // The most privileged role is the one shown in the UI.
        Role primary = user.getRoles().stream()
                .min(Comparator.comparing(Role::getHierarchyLevel))
                .orElse(null);

        return new AuthDtos.UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getInitials(),
                user.getPosition(),
                user.getAvatarUrl(),
                user.getTenant() == null ? null : user.getTenant().getId(),
                user.getTenant() == null ? null : user.getTenant().getName(),
                user.getTenant() == null ? "KES" : user.getTenant().getCurrency(),
                user.getDepartment() == null ? null : user.getDepartment().getName(),
                user.getBranch() == null ? null : user.getBranch().getName(),
                user.collectRoleCodes(),
                primary == null ? null : primary.getName(),
                user.highestHierarchyLevel(),
                user.isPlatformAdmin(),
                user.collectPermissionCodes());
    }

    /** Increments the failure counter and locks the account once the limit is hit. */
    private void registerFailure(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailedLogins) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
            user.setFailedLoginAttempts(0);
            log.warn("Locked account {} after {} failed attempts", user.getEmail(), attempts);
        }
        userRepository.save(user);
    }

    private void recordLogin(User user, String email, LoginStatus status, String reason) {
        loginHistoryRepository.save(LoginHistory.builder()
                .user(user)
                .tenant(user == null ? null : user.getTenant())
                .attemptedEmail(email)
                .status(status)
                .ipAddress(clientIp())
                .userAgent(header("User-Agent"))
                .device(describeDevice(header("User-Agent")))
                .failureReason(reason)
                .occurredAt(LocalDateTime.now())
                .build());
    }

    /** Coarse device label for the login-history table; not a real UA parser. */
    private String describeDevice(String userAgent) {
        if (userAgent == null) {
            return "Unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("iphone") || ua.contains("ipad")) {
            return "iOS";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os")) {
            return "macOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return "Unknown";
    }

    private String clientIp() {
        String forwarded = header("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
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

    /** Exposed for the invitation flow, which needs the same policy applied. */
    public Set<String> passwordRules() {
        return Set.of(
                "At least 8 characters",
                "One upper case letter",
                "One lower case letter",
                "One number",
                "One special character");
    }

    /** Marks an invitation accepted without a password change (already validated). */
    @Transactional
    public void completeInvitation(UserInvitation invitation) {
        invitation.setStatus(com.biashara.common.enums.ApprovalStatus.APPROVED);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(invitation);
    }
}
