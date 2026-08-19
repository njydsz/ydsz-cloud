package com.njydsz.agent.server.agent;

import java.util.List;
import java.util.function.Consumer;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.DagProgressEvent;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatResponse;

/**
 * Agent 应用门面（Application Facade）
 *
 * <p>解耦 Web 控制层与内部服务（ChatService / AgentFactory / AgentDefinitionService），提供统一的 Agent 执行与对话能力入口。
 *
 * <h3>职责边界</h3>
 *
 * <ul>
 *   <li><b>对外</b>：为 Controller 提供简化的-chat/execute/stream/history 接口，屏蔽内部服务协调逻辑
 *   <li><b>对内</b>：编排 ChatService（简单对话）、AgentFactory（多类型 Agent 执行）、AgentDefinitionService（定义管理），
 *       自身不包含业务逻辑
 * </ul>
 *
 * <h3>设计原则</h3>
 *
 * <ul>
 *   <li>遵循六边形架构的"端口"思想：Controller 仅依赖本接口，不直接引用内部 Service
 *   <li>为实现类替换、Mock 测试、AOP 拦截提供统一切入点
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AgentFacade {

  /**
   * 同步对话（简单 LLM 调用，无工具）。
   *
   * @param conversationId 对话 ID（null 则新建）
   * @param userMessage 用户消息
   * @param systemPrompt 系统提示词（null 则使用默认）
   * @return LLM 响应
   */
  ChatResponse chat(String conversationId, String userMessage, String systemPrompt);

  /**
   * 同步执行 Agent（支持 ReAct / RAG / Plan-Execute / Supervisor / DAG 等类型）。
   *
   * @param request Agent 执行请求
   * @return Agent 响应
   */
  ChatResponse execute(AgentExecutionRequest request);

  /**
   * 流式对话（SSE）。
   *
   * @param conversationId 对话 ID
   * @param userMessage 用户消息
   * @param systemPrompt 系统提示词
   * @param chunkConsumer 流式片段消费者
   */
  void stream(
      String conversationId,
      String userMessage,
      String systemPrompt,
      Consumer<ChatChunk> chunkConsumer);

  /**
   * 流式执行 Agent（SSE）。
   *
   * @param request Agent 执行请求
   * @param chunkConsumer 流式片段消费者
   */
  void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer);

  /**
   * 流式执行 Agent（SSE，带进度回调）。
   *
   * <p>支持细粒度进度的 Agent 类型（如 DAG）通过本方法推送节点级进度事件； 不支持的 Agent 类型退化为普通流式调用（忽略 progressConsumer）。
   *
   * @param request Agent 执行请求
   * @param chunkConsumer 流式片段消费者
   * @param progressConsumer DAG 节点进度事件消费者（可为 null）
   */
  void executeStream(
      AgentExecutionRequest request,
      Consumer<ChatChunk> chunkConsumer,
      Consumer<DagProgressEvent> progressConsumer);

  /**
   * 获取对话历史。
   *
   * @param conversationId 对话 ID
   * @return 消息列表
   */
  List<ChatMessage> getHistory(String conversationId);

  /**
   * 清除对话历史。
   *
   * @param conversationId 对话 ID
   */
  void clearHistory(String conversationId);
}
