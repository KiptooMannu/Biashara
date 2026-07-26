package com.biashara.iam.domain;

import com.biashara.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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

import java.util.HashSet;
import java.util.Set;

/**
 * A named bundle of permissions.
 *
 * {@code hierarchyLevel} encodes the creation hierarchy: a user may only create,
 * edit or assign roles at a level strictly greater (i.e. less privileged) than
 * their own. That single integer is what stops a Finance Manager from minting a
 * Business Owner.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Role extends BaseEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    /** 0 = platform super admin, 10 = business owner, 20 = admin, 30 = GM, 40 = dept manager, 50 = staff. */
    @Column(nullable = false)
    private Integer hierarchyLevel;

    /** Null for built-in platform roles; set for roles a business defines itself. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    /** Restricts this role's scope to one department, e.g. HR Officer under HR. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** System roles cannot be deleted or have their code changed. */
    @Builder.Default
    @Column(nullable = false)
    private boolean systemRole = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    public void grant(Permission permission) {
        permissions.add(permission);
    }
}
