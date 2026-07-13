package com.njydsz.pmis.agent.web.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.pmis.agent.domain.model.ChatResponse;
import com.njydsz.pmis.agent.domain.trace.TraceRecorder;
import com.njydsz.pmis.agent.server.debug.AgentDebuggerService;
import com.njydsz.pmis.common.core.response.BaseResponse;

/**
 * Agent 调试 REST API
 *
 * <p>提供执行链路查询和重放接口，用于开发调试。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@RestController
@RequestMapping("/agent/debug")
public class DebugController {

    private static final Logger log = LoggerFactory.getLogger(DebugController.class);

    private final AgentDebuggerService debuggerService;

    public DebugController(AgentDebuggerService debuggerService) {
        this.debuggerService = debuggerService;
    }

    /**
     * 查询执行链路详情
     */
    @GetMapping("/traces/{traceId}")
    public BaseResponse<List<TraceRecorder.TraceStep>> getTrace(
            @PathVariable String traceId) {
        return BaseResponse.success(debuggerService.getTrace(traceId));
    }

    /**
     * 列出最近链路
     */
    @GetMapping("/traces")
    public BaseResponse<List<String>> listTraces(
            @RequestParam(defaultValue = "10") int limit) {
        return BaseResponse.success(debuggerService.listTraces(limit));
    }

    /**
     * 重放执行
     */
    @PostMapping("/replay")
    public BaseResponse<ChatResponse> replay(
            @RequestParam String conversationId,
            @RequestParam String userInput,
            @RequestParam(defaultValue = "CHAT") String agentType) {
        log.info("[Debug-API] 重放: convId={}, agentType={}", conversationId, agentType);
        return BaseResponse.success(debuggerService.replay(conversationId, userInput, agentType));
    }
}
