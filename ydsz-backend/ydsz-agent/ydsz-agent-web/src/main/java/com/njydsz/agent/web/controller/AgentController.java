package com.njydsz.agent.web.controller;

import java.io.IOException;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import java.time.LocalDateTime;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.agent.api.dto.AgentExecutionRequestDTO;
import com.njydsz.agent.api.dto.ChatResponseDTO;
import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.server.agent.AgentFactory;
import com.njydsz.agent.server.chat.AgentRequestGuard;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.vo.ChatResponseDTOVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 执行 Controller（同步 / SSE 流式执行）
 *
 * <p>提供 Agent 的核心执行能力，对外暴露智能体（Agent）的同步与流式调用入口：
 * <ul>
 *   <li>{@code POST /api/v1/agent/execute} - 同步执行 Agent，等待完整响应后返回</li>
 *   <li>{@code POST /api/v1/agent/execute/stream} - SSE 流式执行 Agent，逐 chunk 推送 LLM 响应</li>
 * </ul>
 *
 * <p><b>拆分说明：</b>本类从原 {@code AgentController} 拆分而来，仅保留 Agent 执行相关接口。
 * 可用模型 / 已注册工具等元数据查询接口见 {@link AgentMetadataController}。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>同步 / 流式双模式执行（流式支持心跳保活和客户端断连检测）</li>
 *   <li>多 LLM Provider 路由（通过 {@link AgentFactory} 统一抽象）</li>
 *   <li>幂等防重（5s TTL）+ 限流（50 QPS）+ 审计日志</li>
 * </ul>
 *
 * <h3>SSE 实现细节</h3>
 * <ul>
 *   <li>使用虚拟线程（{@code Thread.startVirtualThread}）承载流式执行，避免阻塞 Web 容器线程</li>
 *   <li>心跳线程每 {@value #HEARTBEAT_INTERVAL_SECONDS} 秒发送 {@code keep-alive} 注释帧保活</li>
 *   <li>客户端断开时通过 {@code active} 标志中断执行，节省 LLM Token</li>
 *   <li>SSE 超时 {@value #SSE_TIMEOUT} 毫秒（2 分钟），超时后自动 cleanup</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）</li>
 *   <li>所有写操作均加 {@link RateLimit} 限流（50 QPS）</li>
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志</li>
 *   <li>执行异常时调用 {@link AgentRequestGuard#releaseIdempotent} 主动释放幂等锁</li>
 * </ul>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 Chat UI / Agent 调用方
 *     → ydsz-gateway
 *       → ydsz-agent-web（本 Controller）
 *         → ydsz-agent-server.AgentFactory
 *           → LlmClient（OpenAI / Claude / 通义千问 / 文心一言 ...）
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see AgentMetadataController 元数据查询接口（可用模型 / 已注册工具）
 * @see AgentFactory Agent 工厂
 * @see AgentRequestGuard 请求守卫（幂等 + 限流 + 业务校验）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "Agent 执行", description = "Agent 同步 / SSE 流式执行")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    /** SSE 超时时间（毫秒） */
    private static final long SSE_TIMEOUT = 120_000L;
    /** 心跳间隔（秒） */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    /** Agent 工厂（根据 agentCode 创建/缓存执行器） */
    private final AgentFactory agentFactory;
    /** 请求守卫（幂等 + 限流 + 业务校验） */
    private final AgentRequestGuard requestGuard;
    /** 心跳调度器（虚拟线程工厂创建，JVM 关闭时自动停止） */
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newScheduledThreadPool(2, Thread.ofVirtual().name("agent-exec-heartbeat-", 0).factory());

    /**
     * 容器关闭时停止心跳调度器。
     */
    @PreDestroy
    public void destroy() {
        heartbeatScheduler.shutdownNow();
    }

    /**
     * 同步执行 Agent，等待完整响应后返回。
     *
     * <p>适用于非实时对话场景（自动化任务、批处理等），由 {@link AgentFactory#getDefaultExecutor()}
     * 获取默认执行器并执行；执行异常时主动调用 {@link AgentRequestGuard#releaseIdempotent} 释放幂等锁，
     * 避免请求失败后 5 秒内重试被误判为重复。
     *
     * @param request Agent 执行请求体（含 agentCode / userInput / systemPrompt / maxIterations / enabledTools）
     * @return 统一响应结果，data 为 {@link ChatResponseDTOVO}（含 content/model/usage）
     */
    @Audit(module = "Agent管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'execute'")
    @Idempotent(key = "ydsz:agent:AgentController:execute:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.agent.execute", threshold = 50)
    @PostMapping("/execute")
    @Operation(summary = "同步执行 Agent", description = "等待完整响应后返回，适用于非实时对话场景")
    public BaseResponse<ChatResponseDTOVO> execute(
            @Valid @RequestBody AgentExecutionRequestDTO request) {
        log.info("[Agent-API] 执行请求: agentCode={}, stream={}",
                request.getAgentCode(), request.isStream());
        requestGuard.check(request.getRequestId(), null);
        AgentExecutionRequest execReq = toExecutionRequest(request);
        try {
            ChatResponse response = agentFactory.getDefaultExecutor().execute(execReq);
            return BaseResponse.success(AgentConverter.INSTANT.entityToVO(toDTO(response)));
        } catch (Exception e) {
            requestGuard.releaseIdempotent(request.getRequestId());
            throw e;
        }
    }

    /**
     * 流式执行 Agent（SSE 实时推送）。
     *
     * <p>基于 Server-Sent Events 逐 chunk 推送 LLM 响应内容，适合实时对话和长文本生成场景。
     *
     * <p>实现细节：
     * <ul>
     *   <li>每 {@value #HEARTBEAT_INTERVAL_SECONDS} 秒发送 {@code keep-alive} 注释帧保活，防止中间代理断连</li>
     *   <li>使用虚拟线程承载 LLM 调用，节省线程资源</li>
     *   <li>客户端断开后通过 {@code active} 标志终止 LLM 调用，节省 Token 成本</li>
     *   <li>SSE 超时 {@value #SSE_TIMEOUT} 毫秒后自动 cleanup</li>
     *   <li>事件类型：{@code chunk}（增量内容）/ {@code done}（正常结束）/ {@code error}（异常结束）</li>
     * </ul>
     *
     * @param request Agent 执行请求体
     * @return SseEmitter（Spring MVC 的 SSE 句柄）
     */
    @Audit(module = "Agent管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'executeStream'")
    @Idempotent(key = "ydsz:agent:AgentController:executeStream:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.agent.executeStream", threshold = 50)
    @PostMapping(value = "/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式执行 Agent（SSE）", description = "逐 chunk 推送 LLM 响应，支持心跳保活和断连检测")
    public SseEmitter executeStream(@Valid @RequestBody AgentExecutionRequestDTO request) {
        log.info("[Agent-API] 流式执行请求: agentCode={}", request.getAgentCode());
        requestGuard.check(request.getRequestId(), null);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        AgentExecutionRequest execReq = toExecutionRequest(request);
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
                agentFactory.getDefaultExecutor().executeStream(execReq, chunk -> {
                    if (!active.get()) {
                        throw new RuntimeException("SSE 连接已断开，终止 Agent 执行");
                    }
                    try {
                        emitter.send(SseEmitter.event()
                                .data(Map.of(
                                        "content", chunk.getDeltaContent() != null ? chunk.getDeltaContent() : "",
                                        "finished", chunk.isFinished()))
                                .name("chunk"));
                    } catch (IOException e) {
                        active.set(false);
                        log.warn("[Agent-API] SSE 发送失败，标记连接断开: {}", e.getMessage());
                    }
                });
                if (active.get()) {
                    emitter.send(SseEmitter.event().data(Map.of("content", "", "finished", true)).name("done"));
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("[Agent-API] 流式执行异常: {}", e.getMessage(), e);
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
     * DTO → 内部执行请求转换。
     */
    private AgentExecutionRequest toExecutionRequest(AgentExecutionRequestDTO dto) {
        return AgentExecutionRequest.builder()
                .conversationId(dto.getConversationId())
                .userInput(dto.getUserInput())
                .systemPrompt(dto.getSystemPrompt())
                .maxIterations(dto.getMaxIterations() != null ? dto.getMaxIterations() : 10)
                .enabledTools(dto.getEnabledTools())
                .build();
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
