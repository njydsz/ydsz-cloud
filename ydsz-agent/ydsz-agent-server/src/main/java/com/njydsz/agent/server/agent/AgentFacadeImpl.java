package com.njydsz.agent.server.agent;

import java.util.List;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.server.chat.ChatService;

/**
 * Agent 应用门面实现
 *
 * <p>编排 {@link ChatService}（简单对话）与 {@link AgentFactory}（多类型 Agent 执行），为 Controller 提供统一入口。
 *
 * <h3>职责说明</h3>
 *
 * <ul>
 *   <li><b>简单对话</b>（chat / stream / history / clearHistory）→ 委托 {@link ChatService}
 *   <li><b>Agent 执行</b>（execute / executeStream）→ 委托 {@link AgentFactory#getDefaultExecutor()}
 * </ul>
 *
 * <p>本类不包含业务逻辑，仅做服务编排与路由。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentFacadeImpl implements AgentFacade {

  /** 简单对话服务（单轮 LLM 调用） */
  private final ChatService chatService;

  /** Agent 工厂（多类型 Agent 执行器创建 / 路由） */
  private final AgentFactory agentFactory;

  /**
   * {@inheritDoc}
   *
   * <p>委托 {@link ChatService#chat} 执行简单 LLM 对话。
   */
  @Override
  public ChatResponse chat(String conversationId, String userMessage, String systemPrompt) {
    return chatService.chat(conversationId, userMessage, systemPrompt);
  }

  /**
   * {@inheritDoc}
   *
   * <p>委托 {@link AgentFactory#getDefaultExecutor()} 获取默认执行器并执行 Agent。
   */
  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    return agentFactory.getDefaultExecutor().execute(request);
  }

  /**
   * {@inheritDoc}
   *
   * <p>委托 {@link ChatService#stream} 执行简单流式对话。
   */
  @Override
  public void stream(
      String conversationId,
      String userMessage,
      String systemPrompt,
      Consumer<ChatChunk> chunkConsumer) {
    chatService.stream(conversationId, userMessage, systemPrompt, chunkConsumer);
  }

  /**
   * {@inheritDoc}
   *
   * <p>委托 {@link AgentFactory#getDefaultExecutor()} 获取默认执行器并执行流式 Agent。
   */
  @Override
  public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
    agentFactory.getDefaultExecutor().executeStream(request, chunkConsumer);
  }

  /**
   * {@inheritDoc}
   *
   * <p>委托 {@link ChatService#getHistory} 获取对话历史。
   */
  @Override
  public List<ChatMessage> getHistory(String conversationId) {
    return chatService.getHistory(conversationId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>委托 {@link ChatService#clearHistory} 清除对话历史。
   */
  @Override
  public void clearHistory(String conversationId) {
    chatService.clearHistory(conversationId);
  }
}
