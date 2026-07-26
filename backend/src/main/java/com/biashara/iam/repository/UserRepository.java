package com.biashara.iam.repository;

import com.biashara.common.enums.UserStatus;
import com.biashara.iam.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Login lookup. Roles and their permissions are fetched eagerly here because
     * the authentication filter needs the full authority set on every request.
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions", "directPermissions", "tenant", "department", "branch"})
    Optional<User> findByEmailIgnoreCaseAndDeletedFalse(String email);

    @EntityGraph(attributePaths = {
            "roles", "roles.permissions", "directPermissions", "tenant", "department", "branch"})
    Optional<User> findByIdAndDeletedFalse(Long id);

    boolean existsByEmailIgnoreCaseAndDeletedFalse(String email);

    @EntityGraph(attributePaths = {
            "roles", "roles.permissions", "directPermissions", "department", "branch", "manager"})
    Page<User> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    List<User> findByTenantIdAndDeletedFalseOrderByFirstNameAsc(Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    long countByTenantIdAndStatusAndDeletedFalse(Long tenantId, UserStatus status);

    List<User> findByTenantIdAndDepartmentIdAndDeletedFalse(Long tenantId, Long departmentId);

    /** Free-text search across the fields an admin actually types into. */
    @EntityGraph(attributePaths = {
            "roles", "roles.permissions", "directPermissions", "department", "branch", "manager"})
    @Query("""
            select u from User u
            where u.tenant.id = :tenantId
              and u.deleted = false
              and (lower(u.firstName) like lower(concat('%', :term, '%'))
                or lower(u.lastName) like lower(concat('%', :term, '%'))
                or lower(u.email) like lower(concat('%', :term, '%'))
                or lower(coalesce(u.employeeNumber, '')) like lower(concat('%', :term, '%')))
            """)
    Page<User> search(@Param("tenantId") Long tenantId, @Param("term") String term, Pageable pageable);

    /** Users holding a given permission, via any of their roles. */
    @Query("""
            select distinct u from User u
            join u.roles r
            join r.permissions p
            where u.tenant.id = :tenantId and u.deleted = false and p.code = :permissionCode
            """)
    List<User> findByPermission(@Param("tenantId") Long tenantId, @Param("permissionCode") String permissionCode);
}
