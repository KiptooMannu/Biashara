package com.biashara.iam.security;

import com.biashara.iam.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The authenticated caller.
 *
 * Carries both permission authorities (bare codes such as {@code sales.pos.operate},
 * which {@code @PreAuthorize("hasAuthority(...)")} checks) and role authorities
 * (prefixed {@code ROLE_}, for the few genuinely role-shaped checks). Holding the
 * tenant id here is what lets every service scope its queries without trusting a
 * caller-supplied parameter.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final Long tenantId;
    private final String tenantName;
    private final String email;
    private final String password;
    private final String fullName;
    private final boolean enabled;
    private final boolean accountLocked;
    private final boolean firstLogin;
    private final boolean platformAdmin;
    private final int hierarchyLevel;
    private final Long departmentId;
    private final Long branchId;
    private final Set<String> permissionCodes;
    private final Set<String> roleCodes;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.tenantId = user.getTenant() == null ? null : user.getTenant().getId();
        this.tenantName = user.getTenant() == null ? null : user.getTenant().getName();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.enabled = user.getStatus() == com.biashara.common.enums.UserStatus.ACTIVE
                || user.getStatus() == com.biashara.common.enums.UserStatus.PENDING_INVITATION;
        this.accountLocked = user.isLocked();
        this.firstLogin = user.isFirstLogin();
        this.platformAdmin = user.isPlatformAdmin();
        this.hierarchyLevel = user.highestHierarchyLevel();
        this.departmentId = user.getDepartment() == null ? null : user.getDepartment().getId();
        this.branchId = user.getBranch() == null ? null : user.getBranch().getId();
        this.permissionCodes = user.collectPermissionCodes();
        this.roleCodes = user.collectRoleCodes();

        List<GrantedAuthority> granted = new ArrayList<>();
        permissionCodes.forEach(code -> granted.add(new SimpleGrantedAuthority(code)));
        roleCodes.forEach(code -> granted.add(new SimpleGrantedAuthority("ROLE_" + code)));
        this.authorities = List.copyOf(granted);
    }

    /** Rebuilt from JWT claims, so a request needs no database round trip. */
    public UserPrincipal(Long id, Long tenantId, String tenantName, String email, String fullName,
                         boolean platformAdmin, int hierarchyLevel, Long departmentId, Long branchId,
                         Set<String> permissionCodes, Set<String> roleCodes) {
        this.id = id;
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.email = email;
        this.password = null;
        this.fullName = fullName;
        this.enabled = true;
        this.accountLocked = false;
        this.firstLogin = false;
        this.platformAdmin = platformAdmin;
        this.hierarchyLevel = hierarchyLevel;
        this.departmentId = departmentId;
        this.branchId = branchId;
        this.permissionCodes = permissionCodes;
        this.roleCodes = roleCodes;

        List<GrantedAuthority> granted = new ArrayList<>();
        permissionCodes.forEach(code -> granted.add(new SimpleGrantedAuthority(code)));
        roleCodes.forEach(code -> granted.add(new SimpleGrantedAuthority("ROLE_" + code)));
        this.authorities = List.copyOf(granted);
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    public boolean has(String permissionCode) {
        return permissionCodes.contains(permissionCode);
    }
}
