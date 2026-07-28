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
import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.vo.ChatResponseDTOVO;

/**
 * 对话 REST API Controller。
 *
 * <p>提供同步和流式两种对话接口，是 Agent 智能对话能力的主要入口：
 * <ul>
 *   <li>{@code POST /agent/chat} - 同步对话，等待完整响应后返回（适用于非实时场景）</li>
 *   <li>{@code POST /agent/chat/stream} - SSE 流式对话，逐 token 推送 LLM 响应（适用于实时聊天）</li>
 *   <li>{@code GET /agent/history} - 按 conversationId 获取对话历史</li>
 *   <li>{@code DELETE /agent/history} - 清除指定 conversationId 的对话历史</li>
 * </ul>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>同步/流式双模式对话（流式支持心跳保活和客户端断连检测）</li>
 *   <li>多轮对话上下文管理（按 conversationId 关联历史消息）</li>
 *   <li>幂等防重 + 限流 + 审计日志（写操作）</li>
 *   <li>执行异常时主动释放幂等锁，避免失败重试被误判为重复</li>
 * </ul>
 *
 * <h3>SSE 实现细节</h3>
 * <ul>
 *   <li>使用虚拟线程承载 LLM 流式调用，节省线程资源</li>
 *   <li>心跳线程每 {@value #HEARTBEAT_INTERVAL_SECONDS} 秒发送 {@code keep-alive} 注释帧保活</li>
 *   <li>客户端断开时通过 {@code active} 标志中断 LLM 调用，节省 Token 成本</li>
 *   <li>SSE 超时 {@value #SSE_TIMEOUT} 毫秒（2 分钟），超时后自动 cleanup</li>
 *   <li>事件类型：{@code chunk}（增量内容）/ {@code done}（正常结束）/ {@code error}（异常结束）</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）</li>
 *   <li>所有写操作均加 {@link RateLimit} 限流（50 QPS）</li>
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/agent")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    /** SSE 超时时间（毫秒） */
    private static final long SSE_TIMEOUT = 120_000L;
    /** 心跳间隔（秒） */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    /** 对话服务（封装同步/流式对话、历史读写、Token 用量统计） */
    private final ChatService chatService;
    /** 请求守卫（幂等 + 限流 + 业务校验） */
    private final AgentRequestGuard requestGuard;
    /** 心跳调度器（虚拟线程工厂创建） */
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newScheduledThreadPool(2, Thread.ofVirtual().name("agent-heartbeat-", 0).factory());

    public ChatController(ChatService chatService, AgentRequestGuard requestGuard) {
        this.chatService = chatService;
        this.requestGuard = requestGuard;
    }

    /**
     * 容器关闭时停止心跳调度器。
     */
    @PreDestroy
    public void destroy() {
        heartbeatScheduler.shutdownNow();
    }

    /**
     * 同步对话。
     *
     * <p>等待 LLM 返回完整响应后返回，适用于非实时对话场景（自动化问答、批处理对话等）。
     * 执行异常时主动调用 {@link AgentRequestGuard#releaseIdempotent} 释放幂等锁，
     * 避免请求失败后 5 秒内重试被误判为重复。
     *
     * @param request 对话请求体（含 conversationId / message / systemPrompt / requestId）
     * @return 统一响应结果，data 为 {@link ChatResponseDTOVO}（含 content/model/usage）
     */
    @Audit(module = "对话管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'chat'")
    @Idempotent(key = "ydsz:agent:ChatController:chat:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.chat.chat", threshold = 50)
    @PostMapping("/chat")
    public BaseResponse<ChatResponseDTOVO> chat(@Valid @RequestBody ChatRequestDTO request) {
        log.info("[Chat-API] 同步对话请求: convId={}, msgLen={}",
                request.getConversationId(), request.getMessage().length());
        requestGuard.check(request.getRequestId(), null);
        try {
            ChatResponse response = chatService.chat(
                    request.getConversationId(),
                    request.getMessage(),
                    request.getSystemPrompt());
            ChatResponseDTO dto = toDTO(response);
            return BaseResponse.success(AgentConverter.INSTANT.entityToVO(dto));
        } catch (Exception e) {
            requestGuard.releaseIdempotent(request.getRequestId());
            throw e;
        }
    }

    /**
     * 流式对话（SSE 实时推送）。
     *
     * <p>基于 Server-Sent Events 逐 token 推送 LLM 响应内容，适用于实时聊天场景。
     *
     * <p>实现细节：
     * <ul>
     *   <li>每 {@value #HEARTBEAT_INTERVAL_SECONDS} 秒发送 {@code keep-alive} 注释帧保活，防止中间代理断连</li>
     *   <li>使用虚拟线程承载 LLM 调用，节省线程资源</li>
     *   <li>客户端断开后通过 {@code active} 标志终止 LLM 调用，节省 Token 成本</li>
     *   <li>SSE 超时 {@value #SSE_TIMEOUT} 毫秒后自动 cleanup</li>
     *   <li>事件类型：{@code chunk}（增量内容含 finishReason）/ {@code done}（正常结束）/ {@code error}（异常结束）</li>
     * </ul>
     *
     * @param request 对话请求体
     * @return SseEmitter（Spring MVC 的 SSE 句柄）
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
     * 获取指定 conversationId 的对话历史。
     *
     * <p>返回按 created_at 升序的对话消息列表（含用户/系统/助手三方的完整历史），
     * 供前端多轮对话界面回显历史。
     *
     * @param conversationId 会话 ID（{@code ydsz_chat_message.conversation_id}）
     * @return 统一响应结果，data 为 {@code [{id, role, content, createdAt}, ...]} 格式的列表
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
     * 清除指定 conversationId 的对话历史。
     *
     * <p>删除该会话的所有历史消息（包括用户/助手/系统消息），但保留 conversationId 本身。
     * 通常用于用户主动"开启新对话"或隐私合规要求清除历史。
     *
     * @param conversationId 会话 ID
     * @return 统一响应结果
     */
    @Audit(module = "对话管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'clearHistory'")
    @Idempotent(key = "ydsz:agent:ChatController:clearHistory:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.chat.clearHistory", threshold = 50)
    @DeleteMapping("/history")
    public BaseResponse<Void> clearHistory(@RequestParam String conversationId) {
        chatService.clearHistory(conversationId);
        return BaseResponse.success();
    }

    /**
     * ChatResponse → DTO 转换。
     */
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
