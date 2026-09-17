package com.njydsz.agent.domain.gateway;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.njydsz.agent.domain.model.a2a.A2aAgentCard;
import com.njydsz.agent.domain.model.a2a.A2aTask;

/**
 * A2A（Agent-to-Agent）协议客户端接口（防腐层 Gateway）。
 *
 * <p>定义 ydsz-agent 作为 A2A Client 调用外部 Agent 的统一契约，隔离 A2A 协议细节 （JSON-RPC、Task 轮询、SSE）与上层业务逻辑。
 *
 * <p>核心操作：
 *
 * <ul>
 *   <li>{@link #fetchAgentCard} — 获取外部 Agent 的能力描述（AgentCard 发现）
 *   <li>{@link #sendMessage} — 向外部 Agent 发送消息并创建 Task
 *   <li>{@link #getTask} — 查询 Task 当前状态和结果
 *   <li>{@link #cancelTask} — 取消进行中的 Task
 * </ul>
 *
 * <p>实现类通过 {@link com.njydsz.agent.infra.a2a.HttpA2aClient} 提供基于 HTTP JSON-RPC 的默认实现。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public interface A2aClient {

  /**
   * 获取远程 Agent 的 AgentCard。
   *
   * <p>AgentCard 描述远程 Agent 的能力、接口地址和认证方式。通常从 {@code /.well-known/agent.json} 端点发现。
   *
   * @param agentUrl Agent 基础 URL
   * @return 远程 Agent 的 AgentCard
   * @throws A2aException A2A 调用失败
   */
  A2aAgentCard fetchAgentCard(String agentUrl);

  /**
   * 异步获取远程 Agent 的 AgentCard。
   *
   * @param agentUrl Agent 基础 URL
   * @return CompletableFuture&lt;A2aAgentCard&gt;
   */
  CompletableFuture<A2aAgentCard> fetchAgentCardAsync(String agentUrl);

  /**
   * 向远程 Agent 发送消息（A2A sendMessage 方法）。
   *
   * <p>创建一个新 Task 并返回 Task ID。后续通过 {@link #getTask} 轮询获取结果。
   *
   * @param agentUrl 远程 Agent URL
   * @param message 消息文本（简单文本场景）
   * @param metadata 扩展元数据（可为 null）
   * @return 创建的 Task（含 id 和初始状态）
   * @throws A2aException A2A 调用失败
   */
  A2aTask sendMessage(String agentUrl, String message, Map<String, Object> metadata);

  /**
   * 异步发送消息到远程 Agent。
   *
   * @param agentUrl 远程 Agent URL
   * @param message 消息文本
   * @param metadata 扩展元数据
   * @return CompletableFuture&lt;A2aTask&gt;
   */
  CompletableFuture<A2aTask> sendMessageAsync(
      String agentUrl, String message, Map<String, Object> metadata);

  /**
   * 获取 Task 当前状态和结果（A2A getTask 方法）。
   *
   * <p>轮询此接口获取任务进展直至达到终态。
   *
   * @param agentUrl 远程 Agent URL
   * @param taskId Task ID
   * @return 最新 Task 状态
   * @throws A2aException A2A 调用失败
   */
  A2aTask getTask(String agentUrl, String taskId);

  /**
   * 轮询 Task 直至终态或超时。
   *
   * <p>每 {@link com.njydsz.agent.domain.config.properties.A2aProperties#getPollInterval()} 轮询一次，
   * 最多轮询 {@link com.njydsz.agent.domain.config.properties.A2aProperties#getMaxPollRetries()} 次。
   *
   * @param agentUrl 远程 Agent URL
   * @param taskId Task ID
   * @param timeout 总超时时间
   * @return 终态 Task
   * @throws A2aException A2A 调用失败或超时
   */
  A2aTask pollTaskUntilCompleted(String agentUrl, String taskId, Duration timeout);

  /**
   * 取消进行中的 Task（A2A cancelTask 方法）。
   *
   * @param agentUrl 远程 Agent URL
   * @param taskId Task ID
   * @return 取消后的 Task（状态为 canceled）
   * @throws A2aException A2A 调用失败
   */
  A2aTask cancelTask(String agentUrl, String taskId);

  /**
   * 检测远程 Agent 的 A2A 接口是否可用。
   *
   * @param agentUrl Agent 基础 URL
   * @return true=AgentCard 可获取
   */
  boolean isAgentAvailable(String agentUrl);
}
