package com.biashara.ai.repository;

import com.biashara.ai.domain.AiInsight;
import com.biashara.common.enums.InsightType;
import com.biashara.common.enums.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiInsightRepository extends JpaRepository<AiInsight, Long> {

    List<AiInsight> findByTenantIdAndDismissedFalseAndDeletedFalseOrderByGeneratedAtDesc(Long tenantId);

    Page<AiInsight> findByTenantIdAndDeletedFalseOrderByGeneratedAtDesc(Long tenantId, Pageable pageable);

    List<AiInsight> findByTenantIdAndModuleAndDismissedFalseAndDeletedFalseOrderByGeneratedAtDesc(
            Long tenantId, String module);

    List<AiInsight> findByTenantIdAndTypeAndDeletedFalse(Long tenantId, InsightType type);

    /** Critical and warning insights are what the dashboard surfaces first. */
    List<AiInsight> findByTenantIdAndSeverityAndDismissedFalseAndDeletedFalseOrderByGeneratedAtDesc(
            Long tenantId, Severity severity);

    long countByTenantIdAndReadFalseAndDismissedFalseAndDeletedFalse(Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);
}
