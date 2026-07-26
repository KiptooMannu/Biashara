package com.biashara.hr.repository;

import com.biashara.common.enums.ApprovalStatus;
import com.biashara.hr.domain.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @EntityGraph(attributePaths = {"employee", "approvedBy"})
    Page<LeaveRequest> findByTenantIdAndDeletedFalseOrderByStartDateDesc(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"employee"})
    List<LeaveRequest> findByTenantIdAndStatusAndDeletedFalseOrderByStartDateAsc(
            Long tenantId, ApprovalStatus status);

    long countByTenantIdAndStatusAndDeletedFalse(Long tenantId, ApprovalStatus status);

    long countByTenantIdAndDeletedFalse(Long tenantId);
}
