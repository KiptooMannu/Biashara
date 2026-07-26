package com.biashara.iam.repository;

import com.biashara.common.enums.ApprovalStatus;
import com.biashara.iam.domain.UserInvitation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, Long> {

    Optional<UserInvitation> findByToken(String token);

    @EntityGraph(attributePaths = {"user", "invitedBy"})
    List<UserInvitation> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(Long tenantId);

    @EntityGraph(attributePaths = {"user", "invitedBy"})
    List<UserInvitation> findByTenantIdAndStatusAndDeletedFalse(Long tenantId, ApprovalStatus status);

    Optional<UserInvitation> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
