package com.biashara.iam.domain;

import com.biashara.common.domain.TenantAwareEntity;
import com.biashara.common.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A system login.
 *
 * Users are never self-registered except the founding Business Owner: every other
 * account is created by an authorised administrator, lands in
 * {@link UserStatus#PENDING_INVITATION} with a generated temporary password, and
 * is forced through a password change on first login.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true),
        @Index(name = "idx_user_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class User extends TenantAwareEntity {

    @Column(nullable = false, unique = true)
    private String email;

    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String phone;
    private String nationalId;
    private String avatarUrl;

    // --- Employment context -------------------------------------------------

    private String employeeNumber;
    private String position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /** Reporting line, used to scope "my team" views. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    private LocalDate employmentDate;

    // --- Access -------------------------------------------------------------

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    /**
     * Permissions granted to this user directly, on top of their roles. Lets an
     * admin make a one-off exception without inventing a whole new role.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_direct_permissions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @Builder.Default
    private Set<Permission> directPermissions = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    // --- Security state -----------------------------------------------------

    /** Forces a redirect to the change-password screen until cleared. */
    @Builder.Default
    @Column(nullable = false)
    private boolean firstLogin = true;

    @Builder.Default
    @Column(nullable = false)
    private boolean twoFactorEnabled = false;

    private String twoFactorSecret;

    @Builder.Default
    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    /** Non-null and in the future means the account is locked out. */
    private LocalDateTime lockedUntil;

    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime passwordChangedAt;

    /** Super admins operate across tenants; every other user is confined to theirs. */
    @Builder.Default
    @Column(nullable = false)
    private boolean platformAdmin = false;

    // --- Derived ------------------------------------------------------------

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getInitials() {
        return ("" + firstName.charAt(0) + lastName.charAt(0)).toUpperCase();
    }

    /** Union of role permissions and direct grants — the set the JWT carries. */
    public Set<String> collectPermissionCodes() {
        Set<String> codes = roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toCollection(HashSet::new));
        directPermissions.forEach(permission -> codes.add(permission.getCode()));
        return codes;
    }

    public Set<String> collectRoleCodes() {
        return roles.stream().map(Role::getCode).collect(Collectors.toSet());
    }

    /** Most privileged (numerically lowest) level across this user's roles. */
    public int highestHierarchyLevel() {
        return roles.stream()
                .map(Role::getHierarchyLevel)
                .min(Integer::compareTo)
                .orElse(Integer.MAX_VALUE);
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }
}
