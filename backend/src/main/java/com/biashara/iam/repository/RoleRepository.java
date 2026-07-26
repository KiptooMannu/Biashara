package com.biashara.iam.repository;

import com.biashara.iam.domain.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = {"permissions", "department"})
    Optional<Role> findByCode(String code);

    @EntityGraph(attributePaths = {"permissions", "department"})
    Optional<Role> findByIdAndDeletedFalse(Long id);

    /** Roles available to a business: its own, plus the built-in system roles. */
    @EntityGraph(attributePaths = {"permissions", "department"})
    @Query("""
            select r from Role r
            where r.deleted = false and (r.tenant.id = :tenantId or r.tenant is null)
            order by r.hierarchyLevel asc, r.name asc
            """)
    List<Role> findAvailableToTenant(@Param("tenantId") Long tenantId);

    /**
     * Roles a user at {@code actorLevel} is allowed to assign — strictly less
     * privileged than themselves.
     */
    @EntityGraph(attributePaths = {"permissions", "department"})
    @Query("""
            select r from Role r
            where r.deleted = false
              and (r.tenant.id = :tenantId or r.tenant is null)
              and r.hierarchyLevel > :actorLevel
            order by r.hierarchyLevel asc
            """)
    List<Role> findAssignableBy(@Param("tenantId") Long tenantId, @Param("actorLevel") Integer actorLevel);

    boolean existsByCode(String code);
}
