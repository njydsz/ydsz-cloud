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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.agent.api.dto.AgentExecutionRequestDTO;
import com.njydsz.agent.api.dto.ChatResponseDTO;
import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.agent.infra.llm.LlmClientRouter;
import com.njydsz.agent.server.agent.AgentFactory;
import com.njydsz.agent.server.chat.AgentRequestGuard;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.vo.ChatResponseDTOVO;

/**
 * Agent REST API
 *
 * <p>提供 Agent 执行、工具查询等接口：
 * <ul>
 *   <li>{@code POST /agent/execute} — 执行 Agent（同步）</li>
 *   <li>{@code POST /agent/execute/stream} — 执行 Agent（SSE 流式）</li>
 *   <li>{@code GET /agent/tools} — 获取已注册工具列表</li>
 *   <li>{@code GET /agent/trace/{traceId}} — 获取执行链路</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    /** SSE 超时时间（毫秒） */
    private static final long SSE_TIMEOUT = 120_000L;
    /** 心跳间隔（秒） */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    /** Agent 工厂 */
    private final AgentFactory agentFactory;
    /** 工具注册中心 */
    private final ToolRegistry toolRegistry;
    /** 请求守卫 */
    private final AgentRequestGuard requestGuard;
    /** LLM 客户端 */
    private final LlmClient llmClient;
    /** 心跳调度器 */
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newScheduledThreadPool(2, Thread.ofVirtual().name("agent-exec-heartbeat-", 0).factory());

    public AgentController(AgentFactory agentFactory, ToolRegistry toolRegistry,
                           AgentRequestGuard requestGuard, LlmClient llmClient) {
        this.agentFactory = agentFactory;
        this.toolRegistry = toolRegistry;
        this.requestGuard = requestGuard;
        this.llmClient = llmClient;
    }

    /**
     * 获取可用模型/Provider 列表
     */
    @GetMapping("/models")
    public BaseResponse<List<Map<String, Object>>> models() {
        List<Map<String, Object>> result = new ArrayList<>();
        if (llmClient instanceof LlmClientRouter router) {
            for (String provider : router.getAvailableProviders()) {
                result.add(Map.of("provider", provider, "available", true));
            }
        } else {
            result.add(Map.of("provider", llmClient.getProvider(), "available", true));
        }
        return BaseResponse.success(result);
    }

    @PreDestroy
    public void destroy() {
        heartbeatScheduler.shutdownNow();
    }

    /**
     * 执行 Agent（同步）
     */
    @Audit(module = "Agent管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'execute'")
    @Idempotent(key = "ydsz:agent:AgentController:execute:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.agent.execute", threshold = 50)
    @PostMapping("/execute")
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
     * 执行 Agent（SSE 流式）
     *
     * <p>每 {@value #HEARTBEAT_INTERVAL_SECONDS} 秒发送心跳保活事件。
     * 客户端断开后自动中断执行，避免资源浪费。
     */
    @Audit(module = "Agent管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'executeStream'")
    @Idempotent(key = "ydsz:agent:AgentController:executeStream:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.agent.executeStream", threshold = 50)
    @PostMapping(value = "/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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
     * 获取已注册工具列表
     */
    @GetMapping("/tools")
    public BaseResponse<List<Map<String, Object>>> tools() {
        var defs = toolRegistry.getToolDefinitions();
        List<Map<String, Object>> result = defs.stream()
                .map(td -> Map.<String, Object>of(
                        "name", td.getName(),
                        "description", td.getDescription() != null ? td.getDescription() : ""))
                .toList();
        return BaseResponse.success(result);
    }

    private AgentExecutionRequest toExecutionRequest(AgentExecutionRequestDTO dto) {
        return AgentExecutionRequest.builder()
                .conversationId(dto.getConversationId())
                .userInput(dto.getUserInput())
                .systemPrompt(dto.getSystemPrompt())
                .maxIterations(dto.getMaxIterations() != null ? dto.getMaxIterations() : 10)
                .enabledTools(dto.getEnabledTools())
                .build();
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
