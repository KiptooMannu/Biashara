package com.biashara.hr.repository;

import com.biashara.analytics.projection.LabelledValue;
import com.biashara.hr.domain.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @EntityGraph(attributePaths = {"department", "branch", "user"})
    Page<Employee> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"department", "branch", "user", "manager"})
    Optional<Employee> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    @EntityGraph(attributePaths = {"department", "branch", "user"})
    List<Employee> findByTenantIdAndDeletedFalseOrderByFirstNameAsc(Long tenantId);

    Optional<Employee> findByTenantIdAndEmployeeNumberAndDeletedFalse(Long tenantId, String employeeNumber);

    List<Employee> findByTenantIdAndDepartmentIdAndDeletedFalse(Long tenantId, Long departmentId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    long countByTenantIdAndActiveTrueAndDeletedFalse(Long tenantId);

    /** Total monthly payroll commitment — feeds the cash-flow warning. */
    @Query("""
            select coalesce(sum(e.basicSalary + coalesce(e.allowances, 0)), 0) from Employee e
            where e.tenant.id = :tenantId and e.deleted = false and e.active = true
            """)
    BigDecimal totalMonthlyPayroll(@Param("tenantId") Long tenantId);

    @Query("""
            select d.name as label, count(e) as value, count(e) as count
            from Employee e join e.department d
            where e.tenant.id = :tenantId and e.deleted = false and e.active = true
            group by d.name
            order by count(e) desc
            """)
    List<LabelledValue> headcountByDepartment(@Param("tenantId") Long tenantId);

    @EntityGraph(attributePaths = {"department", "branch", "user"})
    List<Employee> findTop5ByTenantIdAndDeletedFalseOrderByPerformanceScoreDesc(Long tenantId);
}
