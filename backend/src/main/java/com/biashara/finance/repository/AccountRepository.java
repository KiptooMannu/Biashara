package com.biashara.finance.repository;

import com.biashara.common.enums.AccountType;
import com.biashara.finance.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByTenantIdAndDeletedFalseOrderByCodeAsc(Long tenantId);

    List<Account> findByTenantIdAndTypeAndDeletedFalseOrderByCodeAsc(Long tenantId, AccountType type);

    Optional<Account> findByTenantIdAndCodeAndDeletedFalse(Long tenantId, String code);

    /** Sum of balances for one account class — the basis of the trial balance. */
    @Query("""
            select coalesce(sum(a.balance), 0) from Account a
            where a.tenant.id = :tenantId and a.deleted = false and a.type = :type
            """)
    BigDecimal sumBalanceByType(@Param("tenantId") Long tenantId, @Param("type") AccountType type);
}
