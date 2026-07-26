package com.biashara.iam.security;

import com.biashara.common.exception.UnauthorisedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Accessor for the caller behind the current request.
 *
 * Services resolve the tenant through here rather than accepting a tenant id as a
 * parameter — a request cannot ask for another business's data because it never
 * gets to name the business.
 */
@Component
public class CurrentUser {

    public Optional<UserPrincipal> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public UserPrincipal require() {
        return find().orElseThrow(() -> new UnauthorisedException("No authenticated user on this request"));
    }

    /** The tenant every query in this request must be scoped to. */
    public Long tenantId() {
        Long tenantId = require().getTenantId();
        if (tenantId == null) {
            throw new UnauthorisedException("This account is not attached to a business");
        }
        return tenantId;
    }

    public Long userId() {
        return require().getId();
    }
}
