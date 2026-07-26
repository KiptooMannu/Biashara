package com.biashara.hr.repository;

import com.biashara.hr.domain.Payroll;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    @EntityGraph(attributePaths = {"employee"})
    Page<Payroll> findByTenantIdAndDeletedFalseOrderByPeriodDesc(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"employee"})
    List<Payroll> findByTenantIdAndPeriodAndDeletedFalse(Long tenantId, String period);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    @Query("""
            select coalesce(sum(p.netPay), 0) from Payroll p
            where p.tenant.id = :tenantId and p.deleted = false and p.period = :period
            """)
    BigDecimal sumNetPayForPeriod(@Param("tenantId") Long tenantId, @Param("period") String period);
}
