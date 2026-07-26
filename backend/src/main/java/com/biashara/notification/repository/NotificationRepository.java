package com.biashara.notification.repository;

import com.biashara.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * A user's inbox: messages addressed to them personally plus business-wide
     * broadcasts. Permission filtering is applied in the service layer, which is
     * where the caller's authority set is known.
     */
    @Query("""
            select n from Notification n
            left join n.recipient r
            where n.tenant.id = :tenantId and n.deleted = false
              and (r.id = :userId or r is null)
            order by n.createdOn desc
            """)
    List<Notification> findInbox(@Param("tenantId") Long tenantId, @Param("userId") Long userId, Pageable pageable);

    Page<Notification> findByTenantIdAndDeletedFalseOrderByCreatedOnDesc(Long tenantId, Pageable pageable);

    @Query("""
            select count(n) from Notification n
            left join n.recipient r
            where n.tenant.id = :tenantId and n.deleted = false and n.read = false
              and (r.id = :userId or r is null)
            """)
    long countUnread(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    @Modifying
    @Query("""
            update Notification n set n.read = true
            where n.tenant.id = :tenantId and n.read = false
              and (n.recipient.id = :userId or n.recipient is null)
            """)
    int markAllRead(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
}
