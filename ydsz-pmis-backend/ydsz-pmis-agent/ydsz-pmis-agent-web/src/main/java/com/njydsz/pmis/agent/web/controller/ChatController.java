package com.njydsz.pmis.agent.web.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.pmis.agent.api.dto.ChatRequestDTO;
import com.njydsz.pmis.agent.api.dto.ChatResponseDTO;
import com.njydsz.pmis.agent.domain.model.ChatMessage;
import com.njydsz.pmis.agent.domain.model.ChatResponse;
import com.njydsz.pmis.agent.server.chat.ChatService;
import com.njydsz.pmis.common.core.response.BaseResponse;

/**
 * 对话 REST API
 *
 * <p>提供同步和流式两种对话接口：
 * <ul>
 *   <li>{@code POST /agent/chat} — 同步对话，返回完整响应</li>
 *   <li>{@code GET /agent/chat/stream} — SSE 流式对话，逐 token 推送</li>
 *   <li>{@code GET /agent/history} — 获取对话历史</li>
 *   <li>{@code DELETE /agent/history} — 清除对话历史</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/agent")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long SSE_TIMEOUT = 120_000L;

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 同步对话
     */
    @PostMapping("/chat")
    public BaseResponse<ChatResponseDTO> chat(@Valid @RequestBody ChatRequestDTO request) {
        log.info("[Chat-API] 同步对话请求: convId={}, msgLen={}",
                request.getConversationId(), request.getMessage().length());
        ChatResponse response = chatService.chat(
                request.getConversationId(),
                request.getMessage(),
                request.getSystemPrompt());
        ChatResponseDTO dto = toDTO(response);
        return BaseResponse.success(dto);
    }

    /**
     * 流式对话（SSE）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequestDTO request) {
        log.info("[Chat-API] 流式对话请求: convId={}", request.getConversationId());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        Thread.startVirtualThread(() -> {
            try {
                chatService.stream(
                        request.getConversationId(),
                        request.getMessage(),
                        request.getSystemPrompt(),
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .data(Map.of(
                                                "content", chunk.getDeltaContent() != null ? chunk.getDeltaContent() : "",
                                                "finished", chunk.isFinished(),
                                                "finishReason", chunk.getFinishReason() != null ? chunk.getFinishReason() : ""))
                                        .name("chunk"));
                            } catch (Exception e) {
                                log.warn("[Chat-API] SSE 发送失败: {}", e.getMessage());
                            }
                        });
                emitter.send(SseEmitter.event().data(Map.of("content", "", "finished", true)).name("done"));
                emitter.complete();
            } catch (Exception e) {
                log.error("[Chat-API] 流式对话异常: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 获取对话历史
     */
    @GetMapping("/history")
    public BaseResponse<List<Map<String, Object>>> history(
            @RequestParam String conversationId) {
        List<ChatMessage> messages = chatService.getHistory(conversationId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            result.add(Map.of(
                    "id", msg.getId(),
                    "role", msg.getRole().getApiValue(),
                    "content", msg.getContent() != null ? msg.getContent() : "",
                    "createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : ""));
        }
        return BaseResponse.success(result);
    }

    /**
     * 清除对话历史
     */
    @DeleteMapping("/history")
    public BaseResponse<Void> clearHistory(@RequestParam String conversationId) {
        chatService.clearHistory(conversationId);
        return BaseResponse.success();
    }

    private ChatResponseDTO toDTO(ChatResponse response) {
        ChatResponseDTO dto = new ChatResponseDTO();
        dto.setContent(response.getContent());
        dto.setModel(response.getModel());
        dto.setRespondedAt(LocalDateTime.now());
        if (response.getUsage() != null) {
            dto.setUsage(new ChatResponseDTO.TokenUsageDTO(
                    response.getUsage().getPromptTokens(),
                    response.getUsage().getCompletionTokens(),
                    response.getUsage().getTotalTokens()));
        }
        return dto;
    }
}
