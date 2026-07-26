package com.biashara.iam.service;

import com.biashara.common.enums.ApprovalStatus;
import com.biashara.common.enums.UserStatus;
import com.biashara.common.exception.BusinessRuleException;
import com.biashara.common.exception.ForbiddenException;
import com.biashara.common.exception.NotFoundException;
import com.biashara.iam.domain.Permission;
import com.biashara.iam.domain.Role;
import com.biashara.iam.domain.Tenant;
import com.biashara.iam.domain.User;
import com.biashara.iam.domain.UserInvitation;
import com.biashara.iam.dto.UserDtos;
import com.biashara.iam.repository.BranchRepository;
import com.biashara.iam.repository.DepartmentRepository;
import com.biashara.iam.repository.PermissionRepository;
import com.biashara.iam.repository.RefreshTokenRepository;
import com.biashara.iam.repository.RoleRepository;
import com.biashara.iam.repository.TenantRepository;
import com.biashara.iam.repository.UserInvitationRepository;
import com.biashara.iam.repository.UserRepository;
import com.biashara.iam.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Creating and administering accounts.
 *
 * Two rules are enforced here rather than left to the UI:
 *
 *  1. Hierarchy — you may only create or assign a role strictly less privileged
 *     than your own. That stops a department manager minting an owner.
 *  2. Department scope — a department manager may only place users in the
 *     department they head. A general manager or above is not scoped.
 *
 * Passwords are never chosen by an administrator. The system generates a temporary
 * one, stores only its hash, and forces a change on first login.
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    /**
     * Ambiguous characters (O/0, l/1/I) are excluded, because these get read off a
     * screen and typed by hand.
     */
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%&*?";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Below this level a user is scoped to their own department. */
    private static final int DEPARTMENT_SCOPED_FROM_LEVEL = 40;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final DepartmentRepository departmentRepository;
    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;
    private final UserInvitationRepository invitationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public UserDtos.CreatedUserResponse createUser(UserPrincipal actor, UserDtos.CreateUserRequest request) {
        Long tenantId = actor.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> NotFoundException.of("Business", tenantId));

        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCaseAndDeletedFalse(email)) {
            throw new BusinessRuleException("An account already exists for " + email);
        }

        Role role = roleRepository.findByIdAndDeletedFalse(request.roleId())
                .orElseThrow(() -> NotFoundException.of("Role", request.roleId()));

        // Rule 1: hierarchy.
        if (role.getHierarchyLevel() <= actor.getHierarchyLevel()) {
            throw new ForbiddenException(
                    "You cannot create a %s — that role is at or above your own level"
                            .formatted(role.getName()));
        }

        // Rule 2: department scope for department-level managers.
        if (actor.getHierarchyLevel() >= DEPARTMENT_SCOPED_FROM_LEVEL && actor.getDepartmentId() != null) {
            if (request.departmentId() == null
                    || !request.departmentId().equals(actor.getDepartmentId())) {
                throw new ForbiddenException(
                        "You can only create users within your own department");
            }
        }

        // Seat limit from the tenant's subscription plan.
        if (tenant.getMaxUsers() != null
                && userRepository.countByTenantIdAndDeletedFalse(tenantId) >= tenant.getMaxUsers()) {
            throw new BusinessRuleException(
                    "Your plan allows %d users. Upgrade to add more.".formatted(tenant.getMaxUsers()));
        }

        String temporaryPassword = generateTemporaryPassword();

        User user = User.builder()
                .tenant(tenant)
                .email(email)
                .username(email.substring(0, email.indexOf('@')))
                // Only the hash is stored; the plaintext exists for this request only.
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .phone(request.phone())
                .nationalId(request.nationalId())
                .employeeNumber(request.employeeNumber())
                .position(request.position())
                .employmentDate(request.employmentDate())
                .roles(new HashSet<>(Set.of(role)))
                .status(UserStatus.PENDING_INVITATION)
                .firstLogin(true)
                .platformAdmin(false)
                .build();

        if (request.departmentId() != null) {
            user.setDepartment(departmentRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.departmentId(), tenantId)
                    .orElseThrow(() -> NotFoundException.of("Department", request.departmentId())));
        }
        if (request.branchId() != null) {
            user.setBranch(branchRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.branchId(), tenantId)
                    .orElseThrow(() -> NotFoundException.of("Branch", request.branchId())));
        }
        if (request.managerId() != null) {
            userRepository.findByIdAndDeletedFalse(request.managerId()).ifPresent(user::setManager);
        }
        if (request.extraPermissionIds() != null && !request.extraPermissionIds().isEmpty()) {
            Set<Permission> extras = new HashSet<>(
                    permissionRepository.findAllById(request.extraPermissionIds()));
            user.setDirectPermissions(extras);
        }

        User saved = userRepository.save(user);
        User actingUser = userRepository.findByIdAndDeletedFalse(actor.getId()).orElse(null);

        String token = UUID.randomUUID().toString();
        String emailBody = renderInvitation(tenant, saved, role, temporaryPassword);

        invitationRepository.save(UserInvitation.builder()
                .tenant(tenant)
                .user(saved)
                .token(token)
                .temporaryPassword(temporaryPassword)
                .invitedBy(actingUser)
                .status(ApprovalStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .emailBody(emailBody)
                .build());

        auditService.recordAs(actingUser, tenant, "CREATE_USER", "Administration",
                "User", saved.getId(), saved.getFullName(),
                "Created as %s in %s".formatted(role.getName(),
                        saved.getDepartment() == null ? "no department" : saved.getDepartment().getName()));

        return new UserDtos.CreatedUserResponse(
                UserDtos.UserResponse.from(saved),
                temporaryPassword,
                token,
                emailBody,
                "%s can now sign in with the temporary password and will be asked to change it."
                        .formatted(saved.getFirstName()));
    }

    @Transactional
    public UserDtos.UserResponse updateUser(UserPrincipal actor, Long userId,
                                            UserDtos.UpdateUserRequest request) {
        User user = requireSameTenant(actor, userId);
        guardNotMoreSenior(actor, user, "edit");

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(request.phone());
        user.setPosition(request.position());

        if (request.departmentId() != null) {
            user.setDepartment(departmentRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.departmentId(), actor.getTenantId())
                    .orElseThrow(() -> NotFoundException.of("Department", request.departmentId())));
        }
        if (request.branchId() != null) {
            user.setBranch(branchRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.branchId(), actor.getTenantId())
                    .orElseThrow(() -> NotFoundException.of("Branch", request.branchId())));
        }
        if (request.managerId() != null) {
            userRepository.findByIdAndDeletedFalse(request.managerId()).ifPresent(user::setManager);
        }

        if (request.roleId() != null) {
            Role role = roleRepository.findByIdAndDeletedFalse(request.roleId())
                    .orElseThrow(() -> NotFoundException.of("Role", request.roleId()));
            if (role.getHierarchyLevel() <= actor.getHierarchyLevel()) {
                throw new ForbiddenException(
                        "You cannot assign %s — that role is at or above your own level"
                                .formatted(role.getName()));
            }
            user.setRoles(new HashSet<>(Set.of(role)));
        }

        if (request.status() != null) {
            UserStatus status = parseStatus(request.status());
            user.setStatus(status);
            // Suspending or disabling an account must end its live sessions.
            if (status == UserStatus.SUSPENDED || status == UserStatus.INACTIVE) {
                refreshTokenRepository.revokeAllForUser(user.getId());
            }
        }

        User saved = userRepository.save(user);
        User actingUser = userRepository.findByIdAndDeletedFalse(actor.getId()).orElse(null);

        auditService.recordAs(actingUser, saved.getTenant(), "UPDATE_USER", "Administration",
                "User", saved.getId(), saved.getFullName(), "Account details or role updated");

        return UserDtos.UserResponse.from(saved);
    }

    /** Generates a fresh temporary password and forces a change on next login. */
    @Transactional
    public UserDtos.PasswordResetResponse resetPassword(UserPrincipal actor, Long userId) {
        User user = requireSameTenant(actor, userId);
        guardNotMoreSenior(actor, user, "reset the password for");

        String temporaryPassword = generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setFirstLogin(true);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setPasswordChangedAt(null);
        userRepository.save(user);

        // Every existing session is invalidated by a reset.
        refreshTokenRepository.revokeAllForUser(userId);

        User actingUser = userRepository.findByIdAndDeletedFalse(actor.getId()).orElse(null);
        auditService.recordAs(actingUser, user.getTenant(), "RESET_PASSWORD", "Administration",
                "User", user.getId(), user.getFullName(),
                "Temporary password issued; all sessions revoked");

        return new UserDtos.PasswordResetResponse(temporaryPassword,
                "%s must change this password at their next sign-in.".formatted(user.getFirstName()));
    }

    /** Clears a lockout without changing the password. */
    @Transactional
    public UserDtos.UserResponse unlock(UserPrincipal actor, Long userId) {
        User user = requireSameTenant(actor, userId);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        User saved = userRepository.save(user);

        auditService.recordAs(
                userRepository.findByIdAndDeletedFalse(actor.getId()).orElse(null),
                saved.getTenant(), "UNLOCK_USER", "Administration",
                "User", saved.getId(), saved.getFullName(), "Account lockout cleared");

        return UserDtos.UserResponse.from(saved);
    }

    /** Soft delete: the account stops working but its history stays readable. */
    @Transactional
    public void deleteUser(UserPrincipal actor, Long userId) {
        if (userId.equals(actor.getId())) {
            throw new BusinessRuleException("You cannot delete your own account");
        }

        User user = requireSameTenant(actor, userId);
        guardNotMoreSenior(actor, user, "delete");

        user.setDeleted(true);
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId);

        auditService.recordAs(
                userRepository.findByIdAndDeletedFalse(actor.getId()).orElse(null),
                user.getTenant(), "DELETE_USER", "Administration",
                "User", user.getId(), user.getFullName(),
                "Soft deleted; audit history preserved");
    }

    // --- Guards -------------------------------------------------------------

    private User requireSameTenant(UserPrincipal actor, Long userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));

        // Tenant isolation: a platform admin may cross tenants, nobody else may.
        if (!actor.isPlatformAdmin()
                && (user.getTenant() == null
                || !user.getTenant().getId().equals(actor.getTenantId()))) {
            throw new NotFoundException("User " + userId + " was not found");
        }
        return user;
    }

    private void guardNotMoreSenior(UserPrincipal actor, User target, String verb) {
        if (target.getId().equals(actor.getId())) {
            return;
        }
        if (target.highestHierarchyLevel() <= actor.getHierarchyLevel()) {
            throw new ForbiddenException(
                    "You cannot %s %s — their role is at or above your own level"
                            .formatted(verb, target.getFullName()));
        }
    }

    private UserStatus parseStatus(String raw) {
        try {
            return UserStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException unknown) {
            throw new BusinessRuleException("Unknown status: " + raw);
        }
    }

    /**
     * Eight characters with at least one of each required class, then shuffled so
     * the classes are not in a predictable position.
     */
    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder();
        password.append(pick(UPPER));
        password.append(pick(LOWER));
        password.append(pick(DIGITS));
        password.append(pick(SYMBOLS));

        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        for (int index = 0; index < 4; index++) {
            password.append(pick(all));
        }

        List<Character> characters = new java.util.ArrayList<>();
        for (char character : password.toString().toCharArray()) {
            characters.add(character);
        }
        java.util.Collections.shuffle(characters, RANDOM);

        StringBuilder shuffled = new StringBuilder();
        characters.forEach(shuffled::append);
        return shuffled.toString();
    }

    private char pick(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }

    private String renderInvitation(Tenant tenant, User user, Role role, String temporaryPassword) {
        return """
                Welcome to BIASHARA

                Hello %s,

                An account has been created for you.

                Company:  %s
                Role:     %s
                Login:    %s
                Password: %s

                Sign in at http://localhost:5173/login

                For security reasons you must change your password after your first login.
                """.formatted(
                user.getFullName(),
                tenant.getName(),
                role.getName(),
                user.getEmail(),
                temporaryPassword);
    }
}
