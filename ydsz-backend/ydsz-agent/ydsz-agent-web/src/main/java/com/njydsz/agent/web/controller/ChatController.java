package com.njydsz.agent.web.controller;

import java.io.IOException;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
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

import com.njydsz.agent.api.dto.ChatRequestDTO;
import com.njydsz.agent.api.dto.ChatResponseDTO;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.server.chat.AgentRequestGuard;
import com.njydsz.agent.server.chat.ChatService;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;

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
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/agent")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long SSE_TIMEOUT = 120_000L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private final ChatService chatService;
    private final AgentRequestGuard requestGuard;
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newScheduledThreadPool(2, Thread.ofVirtual().name("agent-heartbeat-", 0).factory());

    public ChatController(ChatService chatService, AgentRequestGuard requestGuard) {
        this.chatService = chatService;
        this.requestGuard = requestGuard;
    }

    @PreDestroy
    public void destroy() {
        heartbeatScheduler.shutdownNow();
    }

    /**
     * 同步对话
     */
    @Audit(module = "对话管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'chat'")
    @Idempotent(key = "ydsz:agent:ChatController:chat:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.chat.chat", threshold = 50)
    @PostMapping("/chat")
    public BaseResponse<ChatResponseDTO> chat(@Valid @RequestBody ChatRequestDTO request) {
        log.info("[Chat-API] 同步对话请求: convId={}, msgLen={}",
                request.getConversationId(), request.getMessage().length());
        requestGuard.check(request.getRequestId(), null);
        try {
            ChatResponse response = chatService.chat(
                    request.getConversationId(),
                    request.getMessage(),
                    request.getSystemPrompt());
            ChatResponseDTO dto = toDTO(response);
            return BaseResponse.success(dto);
        } catch (Exception e) {
            requestGuard.releaseIdempotent(request.getRequestId());
            throw e;
        }
    }

    /**
     * 流式对话（SSE）
     *
     * <p>每 {@value #HEARTBEAT_INTERVAL_SECONDS} 秒发送心跳保活事件，防止中间代理断连。
     * 客户端断开后自动中断 LLM 调用，避免 Token 浪费。
     */
    @Audit(module = "对话管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'chatStream'")
    @Idempotent(key = "ydsz:agent:ChatController:chatStream:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.chat.chatStream", threshold = 50)
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequestDTO request) {
        log.info("[Chat-API] 流式对话请求: convId={}", request.getConversationId());
        requestGuard.check(request.getRequestId(), null);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        AtomicBoolean active = new AtomicBoolean(true);

        var heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (active.get()) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException e) {
                    active.set(false);
                }
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        Thread virtualThread = Thread.startVirtualThread(() -> {
            try {
                chatService.stream(
                        request.getConversationId(),
                        request.getMessage(),
                        request.getSystemPrompt(),
                        chunk -> {
                            if (!active.get()) {
                                throw new RuntimeException("SSE 连接已断开，终止 LLM 调用");
                            }
                            try {
                                emitter.send(SseEmitter.event()
                                        .data(Map.of(
                                                "content", chunk.getDeltaContent() != null ? chunk.getDeltaContent() : "",
                                                "finished", chunk.isFinished(),
                                                "finishReason", chunk.getFinishReason() != null ? chunk.getFinishReason() : ""))
                                        .name("chunk"));
                            } catch (IOException e) {
                                active.set(false);
                                log.warn("[Chat-API] SSE 发送失败，标记连接断开: {}", e.getMessage());
                            }
                        });
                if (active.get()) {
                    emitter.send(SseEmitter.event().data(Map.of("content", "", "finished", true)).name("done"));
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("[Chat-API] 流式对话异常: {}", e.getMessage(), e);
                if (active.get()) {
                    try {
                        emitter.send(SseEmitter.event()
                                .data(Map.of("error", e.getMessage() != null ? e.getMessage() : "未知错误", "finished", true))
                                .name("error"));
                    } catch (IOException ignored) {
                        // 客户端已断开，忽略
                    }
                    emitter.completeWithError(e);
                }
            }
        });

        Runnable cleanup = () -> {
            active.set(false);
            heartbeatFuture.cancel(true);
            if (virtualThread.isAlive()) {
                virtualThread.interrupt();
            }
        };
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        emitter.onCompletion(cleanup);

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
    @Audit(module = "对话管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'clearHistory'")
    @Idempotent(key = "ydsz:agent:ChatController:clearHistory:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.chat.clearHistory", threshold = 50)
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
