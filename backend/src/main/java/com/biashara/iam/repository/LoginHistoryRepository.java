package com.biashara.iam.repository;

import com.biashara.common.enums.LoginStatus;
import com.biashara.iam.domain.LoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    @EntityGraph(attributePaths = {"user"})
    Page<LoginHistory> findByTenantIdOrderByOccurredAtDesc(Long tenantId, Pageable pageable);

    List<LoginHistory> findTop20ByUserIdOrderByOccurredAtDesc(Long userId);

    /** Recent failures for one email — the basis of the suspicious-activity check. */
    long countByAttemptedEmailIgnoreCaseAndStatusAndOccurredAtAfter(
            String email, LoginStatus status, LocalDateTime since);
}
