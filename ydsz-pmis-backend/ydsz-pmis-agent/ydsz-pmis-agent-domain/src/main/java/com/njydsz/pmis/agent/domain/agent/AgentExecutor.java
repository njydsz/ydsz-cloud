package com.njydsz.pmis.agent.domain.agent;

import java.util.function.Consumer;

import com.njydsz.pmis.agent.domain.model.ChatChunk;
import com.njydsz.pmis.agent.domain.model.ChatResponse;

/**
 * Agent 执行器接口
 *
 * <p>定义 Agent 的核心执行能力。不同实现支持不同 Agent 模式：
 * <ul>
 *   <li>{@code SimpleAgentExecutor} — 单轮 LLM 调用（P0 已在 ChatService 实现）</li>
 *   <li>{@code ReActAgentExecutor} — ReAct 模式（Thought→Action→Observation 循环）</li>
 *   <li>{@code PlanExecuteAgentExecutor} — Plan-and-Execute 模式（P3）</li>
 *   <li>{@code RouterAgentExecutor} — 路由器 Agent（P3）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AgentExecutor {

    /**
     * 同步执行 Agent
     *
     * @param request 执行请求
     * @return 执行结果
     */
    ChatResponse execute(AgentExecutionRequest request);

    /**
     * 流式执行 Agent
     *
     * @param request        执行请求
     * @param chunkConsumer  流式片段消费者
     */
    void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer);

    /**
     * Agent 类型标识
     *
     * @return 类型标识（如 "simple"、"react"、"plan_execute"）
     */
    String getType();

    /**
     * 是否支持指定 Agent 类型
     *
     * @param type Agent 类型
     * @return true=支持
     */
    boolean supports(String type);
}
