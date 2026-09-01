package com.njydsz.agent.server.debug;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.AgentExecutor;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.trace.TraceMeta;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.agent.AgentFactory;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * Agent 调试服务
 *
 * <p>提供执行链路查询和重放能力，用于开发调试和问题排查。
 *
 * <p><b>DDD 合规：</b>通过 {@link TraceRecorder} 域接口访问链路数据，不依赖 infra 实现。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>{@link #getTrace} — 查询执行链路详情
 *   <li>{@link #replay} — 重放指定链路的执行过程
 *   <li>{@link #listTraces} — 列出最近的执行链路
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Service
@Slf4j
public class AgentDebuggerService {

  /** 重放默认温度 */
  private static final double REPLAY_TEMPERATURE = 0.7;

  /** 重放默认最大 Token 数 */
  private static final int REPLAY_MAX_TOKENS = 2048;

  private final TraceRecorder traceRecorder;
  private final AgentFactory agentFactory;

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  public AgentDebuggerService(
      TraceRecorder traceRecorder,
      AgentFactory agentFactory,
      SnowflakeIdGenerator snowflakeIdGenerator) {
    this.traceRecorder = traceRecorder;
    this.agentFactory = agentFactory;
    this.snowflakeIdGenerator = snowflakeIdGenerator;
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
   * @param userInput 用户输入
   * @param agentType Agent 类型（CHAT/REACT/RAG/PLAN_EXECUTE/ROUTER）
   * @return 重放结果
   */
  public ChatResponse replay(String conversationId, String userInput, String agentType) {
    log.info("[Debugger] 重放: convId={}, agentType={}", conversationId, agentType);

    AgentDefinition def =
        new AgentDefinition(
            String.valueOf(snowflakeIdGenerator.nextId()),
            "replay-" + conversationId,
            "Replay Agent",
            AgentDefinition.Type.valueOf(agentType.toUpperCase()),
            null,
            List.of(),
            // 重放使用默认推理参数：temperature 0.7、maxTokens 2048、最大迭代 10
            REPLAY_TEMPERATURE,
            REPLAY_MAX_TOKENS,
            10,
            null);

    AgentExecutor executor = agentFactory.getExecutor(def);
    AgentExecutionRequest request =
        AgentExecutionRequest.builder()
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
    return traceRecorder.listRecentTraces(limit);
  }

  /**
   * 列出最近链路的元数据
   *
   * @param limit 最大数量
   * @return 链路元数据列表，不支持时返回空列表
   */
  public List<TraceMeta> listTraceMetas(int limit) {
    return traceRecorder.listRecentTraceMetas(limit);
  }

  /**
   * 获取链路元数据
   *
   * @param traceId 链路 ID
   * @return 元数据，不支持或不存在时返回 null
   */
  public TraceMeta getTraceMeta(String traceId) {
    return traceRecorder.getTraceMeta(traceId);
  }
}
