package com.biashara.iam.repository;

import com.biashara.iam.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByTenantIdAndDeletedFalseOrderByNameAsc(Long tenantId);

    Optional<Branch> findByTenantIdAndMainBranchTrueAndDeletedFalse(Long tenantId);

    Optional<Branch> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);
}
