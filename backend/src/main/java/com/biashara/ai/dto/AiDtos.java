package com.biashara.ai.dto;

import com.biashara.ai.domain.AiInsight;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AiDtos {

    private AiDtos() {
    }

    public record InsightResponse(
            Long id,
            String type,
            String severity,
            String title,
            String summary,
            String cause,
            String recommendation,
            String metricLabel,
            BigDecimal metricValue,
            String metricUnit,
            BigDecimal changePercent,
            BigDecimal confidence,
            String module,
            String actionUrl,
            String actionLabel,
            String entityType,
            Long entityId,
            String entityName,
            boolean read,
            LocalDateTime generatedAt) {

        public static InsightResponse from(AiInsight insight) {
            return new InsightResponse(
                    insight.getId(),
                    insight.getType().name(),
                    insight.getSeverity().name(),
                    insight.getTitle(),
                    insight.getSummary(),
                    insight.getCause(),
                    insight.getRecommendation(),
                    insight.getMetricLabel(),
                    insight.getMetricValue(),
                    insight.getMetricUnit(),
                    insight.getChangePercent(),
                    insight.getConfidence(),
                    insight.getModule(),
                    insight.getActionUrl(),
                    insight.getActionLabel(),
                    insight.getEntityType(),
                    insight.getEntityId(),
                    insight.getEntityName(),
                    insight.isRead(),
                    insight.getGeneratedAt());
        }
    }

    public record AskRequest(
            @NotBlank(message = "Ask a question")
            @Size(max = 500, message = "Keep the question under 500 characters")
            String question,
            String conversationId) {
    }

    /**
     * An assistant answer.
     *
     * {@code dataPoints} carries the figures the answer was derived from and
     * {@code dataSource} names the query behind it, so an answer can be checked
     * rather than taken on faith.
     */
    public record AnswerResponse(
            String conversationId,
            String question,
            String answer,
            List<String> dataPoints,
            String dataSource,
            List<String> suggestedFollowUps,
            LocalDateTime answeredAt) {
    }

    public record ChatMessageResponse(
            Long id,
            String conversationId,
            String role,
            String content,
            LocalDateTime sentAt) {
    }
}
