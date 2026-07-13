package com.njydsz.pmis.agent.server.debug;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.njydsz.pmis.agent.domain.agent.AgentDefinition;
import com.njydsz.pmis.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.pmis.agent.domain.agent.AgentExecutor;
import com.njydsz.pmis.agent.domain.model.ChatResponse;
import com.njydsz.pmis.agent.domain.trace.TraceRecorder;
import com.njydsz.pmis.agent.server.agent.AgentFactory;

/**
 * Agent 调试服务
 *
 * <p>提供执行链路查询和重放能力，用于开发调试和问题排查。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #getTrace} — 查询执行链路详情</li>
 *   <li>{@link #replay} — 重放指定链路的执行过程</li>
 *   <li>{@link #listTraces} — 列出最近的执行链路</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Service
public class AgentDebuggerService {

    private static final Logger log = LoggerFactory.getLogger(AgentDebuggerService.class);

    private final TraceRecorder traceRecorder;
    private final AgentFactory agentFactory;

    public AgentDebuggerService(TraceRecorder traceRecorder, AgentFactory agentFactory) {
        this.traceRecorder = traceRecorder;
        this.agentFactory = agentFactory;
    }

    /**
     * 查询执行链路详情
     *
     * @param traceId 链路 ID
     * @return 链路步骤列表
     */
    public List<TraceRecorder.TraceStep> getTrace(String traceId) {
        log.info("[Debugger] 查询链路: traceId={}", traceId);
        return traceRecorder.getSteps(traceId);
    }

    /**
     * 重放执行链路
     *
     * <p>根据原始对话 ID 和用户输入，重新执行 Agent 并记录新的链路。
     *
     * @param conversationId 原始对话 ID
     * @param userInput      用户输入
     * @param agentType      Agent 类型（CHAT/REACT/RAG/PLAN_EXECUTE/ROUTER）
     * @return 重放结果
     */
    public ChatResponse replay(String conversationId, String userInput, String agentType) {
        log.info("[Debugger] 重放: convId={}, agentType={}", conversationId, agentType);

        AgentDefinition def = new AgentDefinition(
                java.util.UUID.randomUUID().toString(),
                "replay-" + conversationId,
                "Replay Agent",
                AgentDefinition.Type.valueOf(agentType.toUpperCase()),
                null, java.util.List.of(),
                0.7, 2048, 10,
                null);

        AgentExecutor executor = agentFactory.getExecutor(def);
        AgentExecutionRequest request = AgentExecutionRequest.builder()
                .userInput(userInput)
                .conversationId("replay-" + conversationId)
                .build();

        return executor.execute(request);
    }

    /**
     * 列出最近链路（如果 TraceRecorder 支持的话）
     *
     * @param limit 最大数量
     * @return 链路 ID 列表
     */
    public List<String> listTraces(int limit) {
        if (traceRecorder instanceof com.njydsz.pmis.agent.infra.trace.InMemoryTraceRecorder inMem) {
            return inMem.listRecentTraces(limit);
        }
        return List.of();
    }
}
