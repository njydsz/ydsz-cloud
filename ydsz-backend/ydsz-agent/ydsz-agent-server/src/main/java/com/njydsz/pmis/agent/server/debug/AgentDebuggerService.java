package com.njydsz.agent.server.debug;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.AgentExecutor;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.infra.trace.InMemoryTraceRecorder;
import com.njydsz.agent.server.agent.AgentFactory;

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
 * @author ydsz-team
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
                UUID.randomUUID().toString(),
                "replay-" + conversationId,
                "Replay Agent",
                AgentDefinition.Type.valueOf(agentType.toUpperCase()),
                null, List.of(),
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
        if (traceRecorder instanceof InMemoryTraceRecorder inMem) {
            return inMem.listRecentTraces(limit);
        }
        return List.of();
    }

    /**
     * 列出最近链路的元数据
     *
     * @param limit 最大数量
     * @return 链路元数据列表，不支持时返回空列表
     */
    public List<InMemoryTraceRecorder.TraceMeta> listTraceMetas(int limit) {
        if (traceRecorder instanceof InMemoryTraceRecorder inMem) {
            return inMem.listRecentTraceMetas(limit);
        }
        return List.of();
    }

    /**
     * 获取链路元数据
     *
     * @param traceId 链路 ID
     * @return 元数据，不支持或不存在时返回 null
     */
    public InMemoryTraceRecorder.TraceMeta getTraceMeta(String traceId) {
        if (traceRecorder instanceof InMemoryTraceRecorder inMem) {
            return inMem.getTraceMeta(traceId);
        }
        return null;
    }
}
