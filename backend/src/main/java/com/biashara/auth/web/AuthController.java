package com.biashara.auth.web;

import com.biashara.auth.dto.AuthDtos;
import com.biashara.auth.service.AuthService;
import com.biashara.auth.service.DemoAccountService;
import com.biashara.iam.repository.UserRepository;
import com.biashara.iam.security.CurrentUser;
import com.biashara.common.exception.UnauthorisedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Sign in, token refresh and password management")
public class AuthController {

    private final AuthService authService;
    private final DemoAccountService demoAccountService;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    @PostMapping("/login")
    @Operation(summary = "Sign in and receive an access token")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    public AuthDtos.LoginResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoke the current session")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestParam(required = false) String refreshToken) {
        authService.logout(refreshToken, currentUser.userId());
        return ResponseEntity.ok(Map.of("success", true, "message", "Signed out"));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "The signed-in user, with their roles and permissions")
    public AuthDtos.UserSummary me() {
        return userRepository.findByIdAndDeletedFalse(currentUser.userId())
                .map(authService::toSummary)
                .orElseThrow(() -> new UnauthorisedException("Account no longer exists"));
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Change your own password; required on first login")
    public ResponseEntity<Map<String, Object>> changePassword(
            @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(currentUser.userId(), request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Password changed. Please sign in again."));
    }

    @GetMapping("/password-rules")
    @Operation(summary = "The password policy, for client-side hints")
    public Map<String, Object> passwordRules() {
        return Map.of("rules", authService.passwordRules());
    }

    /**
     * Lists the seeded logins. Present so a reviewer can enter as any role
     * without credentials being passed around out of band; it exposes only
     * accounts created by the seeder and would not exist in a real deployment.
     */
    @GetMapping("/demo-accounts")
    @Operation(summary = "Seeded demo logins, one per role")
    public List<AuthDtos.DemoAccount> demoAccounts() {
        return demoAccountService.listDemoAccounts();
    }
}
