package com.biashara.ai.web;

import com.biashara.ai.dto.AiDtos;
import com.biashara.ai.repository.AiInsightRepository;
import com.biashara.ai.service.AiAssistantService;
import com.biashara.common.exception.NotFoundException;
import com.biashara.iam.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI", description = "Business insights and the assistant")
public class AiController {

    private final AiInsightRepository insightRepository;
    private final AiAssistantService assistantService;
    private final CurrentUser currentUser;

    @GetMapping("/insights")
    @PreAuthorize("hasAuthority('ai.insight.view')")
    @Operation(summary = "Active insights, newest first")
    public List<AiDtos.InsightResponse> insights(@RequestParam(required = false) String module) {
        Long tenantId = currentUser.tenantId();
        var found = module == null || module.isBlank()
                ? insightRepository
                .findByTenantIdAndDismissedFalseAndDeletedFalseOrderByGeneratedAtDesc(tenantId)
                : insightRepository
                .findByTenantIdAndModuleAndDismissedFalseAndDeletedFalseOrderByGeneratedAtDesc(
                        tenantId, module);

        return found.stream().map(AiDtos.InsightResponse::from).toList();
    }

    @PostMapping("/insights/{id}/read")
    @PreAuthorize("hasAuthority('ai.insight.view')")
    @Operation(summary = "Mark an insight as read")
    @Transactional
    public AiDtos.InsightResponse markRead(@PathVariable Long id) {
        var insight = insightRepository.findById(id)
                .filter(candidate -> candidate.getTenant().getId().equals(currentUser.tenantId()))
                .orElseThrow(() -> NotFoundException.of("Insight", id));
        insight.setRead(true);
        return AiDtos.InsightResponse.from(insightRepository.save(insight));
    }

    @PostMapping("/insights/{id}/dismiss")
    @PreAuthorize("hasAuthority('ai.insight.view')")
    @Operation(summary = "Dismiss an insight so it stops appearing")
    @Transactional
    public Map<String, Object> dismiss(@PathVariable Long id) {
        var insight = insightRepository.findById(id)
                .filter(candidate -> candidate.getTenant().getId().equals(currentUser.tenantId()))
                .orElseThrow(() -> NotFoundException.of("Insight", id));
        insight.setDismissed(true);
        insightRepository.save(insight);
        return Map.of("success", true, "message", "Insight dismissed");
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAuthority('ai.assistant.use')")
    @Operation(summary = "Ask the business assistant a question")
    public AiDtos.AnswerResponse ask(@Valid @RequestBody AiDtos.AskRequest request) {
        return assistantService.ask(
                currentUser.tenantId(),
                currentUser.userId(),
                request.question(),
                request.conversationId());
    }

    @GetMapping("/conversations/{conversationId}")
    @PreAuthorize("hasAuthority('ai.assistant.use')")
    @Operation(summary = "Replay one conversation")
    public List<AiDtos.ChatMessageResponse> conversation(@PathVariable String conversationId) {
        return assistantService.history(currentUser.tenantId(), conversationId);
    }

    @GetMapping("/suggested-questions")
    @PreAuthorize("hasAuthority('ai.assistant.use')")
    @Operation(summary = "Starter prompts for an empty assistant screen")
    public Map<String, Object> suggestedQuestions() {
        return Map.of("questions", assistantService.suggestedQuestions());
    }
}
