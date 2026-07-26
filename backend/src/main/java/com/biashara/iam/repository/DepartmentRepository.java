package com.biashara.iam.repository;

import com.biashara.iam.domain.Department;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /** Head is fetched because the listing shows who runs each department. */
    @EntityGraph(attributePaths = {"head"})
    List<Department> findByTenantIdAndDeletedFalseOrderByNameAsc(Long tenantId);

    Optional<Department> findByTenantIdAndCodeAndDeletedFalse(Long tenantId, String code);

    Optional<Department> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);
}
