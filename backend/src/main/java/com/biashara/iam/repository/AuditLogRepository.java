package com.biashara.iam.repository;

import com.biashara.iam.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @EntityGraph(attributePaths = {"actor"})
    Page<AuditLog> findByTenantIdOrderByOccurredAtDesc(Long tenantId, Pageable pageable);

    List<AuditLog> findTop10ByTenantIdOrderByOccurredAtDesc(Long tenantId);

    Page<AuditLog> findByTenantIdAndModuleOrderByOccurredAtDesc(Long tenantId, String module, Pageable pageable);

    List<AuditLog> findByTenantIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
            Long tenantId, String entityType, Long entityId);

    @Query("""
            select a from AuditLog a
            where a.tenant.id = :tenantId
              and (lower(a.actorName) like lower(concat('%', :term, '%'))
                or lower(a.action) like lower(concat('%', :term, '%'))
                or lower(coalesce(a.targetName, '')) like lower(concat('%', :term, '%')))
            order by a.occurredAt desc
            """)
    Page<AuditLog> search(@Param("tenantId") Long tenantId, @Param("term") String term, Pageable pageable);
}
