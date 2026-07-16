package com.njydsz.agent.web.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.api.dto.AgentTraceDetailDTO;
import com.njydsz.agent.api.dto.AgentTraceListDTO;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.trace.TraceRecorder.TraceStep;
import com.njydsz.agent.infra.trace.InMemoryTraceRecorder.TraceMeta;
import com.njydsz.agent.server.debug.AgentDebuggerService;
import com.njydsz.common.core.response.BaseResponse;

/**
 * Agent 调试 REST API
 *
 * <p>提供执行链路查询和重放接口，用于开发调试和问题排查。
 * <ul>
 *   <li>{@code GET /agent/debug/traces} — 列出最近执行链路</li>
 *   <li>{@code GET /agent/debug/trace/{traceId}} — 查询链路详情</li>
 *   <li>{@code POST /agent/debug/trace/{traceId}/replay} — 重放指定链路</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@RestController
@RequestMapping("/agent/debug")
public class DebugController {

    private static final Logger log = LoggerFactory.getLogger(DebugController.class);

    private final AgentDebuggerService agentDebuggerService;

    public DebugController(AgentDebuggerService agentDebuggerService) {
        this.agentDebuggerService = agentDebuggerService;
    }

    /**
     * 列出最近执行链路
     *
     * @param limit 最大数量，默认 20
     * @return 链路列表
     */
    @GetMapping("/traces")
    public BaseResponse<List<AgentTraceListDTO>> listTraces(
            @RequestParam(defaultValue = "20") int limit) {
        List<TraceMeta> metas = agentDebuggerService.listTraceMetas(limit);
        List<AgentTraceListDTO> dtos = metas.stream()
                .map(meta -> {
                    int stepCount = agentDebuggerService.getTrace(meta.getTraceId()).size();
                    return new AgentTraceListDTO(
                            meta.getTraceId(),
                            meta.getConversationId(),
                            meta.getAgentId(),
                            meta.getStatus(),
                            meta.getStartedAt(),
                            meta.getTotalDurationMs(),
                            stepCount);
                })
                .collect(Collectors.toList());
        return BaseResponse.success(dtos);
    }

    /**
     * 查询链路详情
     *
     * @param traceId 链路 ID
     * @return 链路详情（含步骤摘要）
     */
    @GetMapping("/trace/{traceId}")
    public BaseResponse<AgentTraceDetailDTO> getTrace(@PathVariable String traceId) {
        TraceMeta meta = agentDebuggerService.getTraceMeta(traceId);
        String agentType = meta != null ? meta.getAgentId() : "UNKNOWN";
        List<TraceStep> steps = agentDebuggerService.getTrace(traceId);
        String plan = steps.stream()
                .map(step -> "[" + step.getStepIndex() + "] " + step.getStepType() + ": " + step.getContent())
                .collect(Collectors.joining("\n"));
        return BaseResponse.success(new AgentTraceDetailDTO(traceId, agentType, plan));
    }

    /**
     * 重放指定链路
     *
     * <p>从链路元数据中提取原始 conversationId 和 agentType，
     * 从第一个步骤中提取 userInput，重新执行 Agent。
     *
     * @param traceId 链路 ID
     * @return 重放结果内容
     */
    @PostMapping("/trace/{traceId}/replay")
    public BaseResponse<String> replayTrace(@PathVariable String traceId) {
        log.info("[Debug-API] 重放链路: traceId={}", traceId);
        TraceMeta meta = agentDebuggerService.getTraceMeta(traceId);
        if (meta == null) {
            return BaseResponse.failed("TRACE_NOT_FOUND", "链路不存在或不支持重放: " + traceId);
        }
        List<TraceStep> steps = agentDebuggerService.getTrace(traceId);
        if (steps.isEmpty()) {
            return BaseResponse.failed("TRACE_EMPTY", "链路无步骤记录，无法提取重放输入: " + traceId);
        }
        String userInput = steps.get(0).getContent();
        String conversationId = meta.getConversationId();
        String agentType = meta.getAgentId();
        log.info("[Debug-API] 重放参数: convId={}, agentType={}, userInputLen={}",
                conversationId, agentType, userInput != null ? userInput.length() : 0);
        ChatResponse response = agentDebuggerService.replay(conversationId, userInput, agentType);
        return BaseResponse.success(response != null ? response.getContent() : "");
    }
}
