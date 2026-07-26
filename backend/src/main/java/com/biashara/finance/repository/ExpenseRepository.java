package com.biashara.finance.repository;

import com.biashara.analytics.projection.LabelledValue;
import com.biashara.common.enums.ExpenseStatus;
import com.biashara.finance.domain.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @EntityGraph(attributePaths = {"createdBy", "department", "approvedBy"})
    Page<Expense> findByTenantIdAndDeletedFalseOrderByExpenseDateDesc(Long tenantId, Pageable pageable);

    Optional<Expense> findByIdAndTenantIdAndDeletedFalse(Long id, Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    long countByTenantIdAndStatusAndDeletedFalse(Long tenantId, ExpenseStatus status);

    @Query("""
            select coalesce(sum(e.amount), 0) from Expense e
            where e.tenant.id = :tenantId and e.deleted = false
              and e.expenseDate between :from and :to
            """)
    BigDecimal sumBetween(@Param("tenantId") Long tenantId,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to);

    /** Expense breakdown by category, for the composition chart. */
    @Query("""
            select e.category as label, sum(e.amount) as value, count(e) as count
            from Expense e
            where e.tenant.id = :tenantId and e.deleted = false
              and e.expenseDate between :from and :to
            group by e.category
            order by sum(e.amount) desc
            """)
    List<LabelledValue> breakdownByCategory(@Param("tenantId") Long tenantId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /** Recurring commitments, which the cash-flow forecast projects forward. */
    List<Expense> findByTenantIdAndRecurringTrueAndDeletedFalse(Long tenantId);
}
