package com.biashara.ai.repository;

import com.biashara.ai.domain.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    List<AiChatMessage> findByTenantIdAndConversationIdAndDeletedFalseOrderBySentAtAsc(
            Long tenantId, String conversationId);

    List<AiChatMessage> findTop50ByTenantIdAndUserIdAndDeletedFalseOrderBySentAtDesc(Long tenantId, Long userId);
}
