package com.njydsz.agent.server.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.DagProgressEvent;
import com.njydsz.agent.domain.model.BatchChatResult;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.MessageContent;
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

  private static final Logger LOG = LoggerFactory.getLogger(AgentFacadeImpl.class);

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
   * <p>委托 {@link ChatService#chat(String, MessageContent, String)} 执行多模态 LLM 对话。
   */
  @Override
  public ChatResponse chat(String conversationId, MessageContent multimodalContent, String systemPrompt) {
    return chatService.chat(conversationId, multimodalContent, systemPrompt);
  }

  /**
   * {@inheritDoc}
   *
   * <p>使用 JDK 21 {@link StructuredTaskScope} 并行处理多条对话请求：
   *
   * <ul>
   *   <li>每条请求在独立虚拟线程中执行，单条失败不影响其他条目
   *   <li>结果顺序与请求 items 顺序一致
   *   <li>总耗时取所有线程中最长者（并行加速）
   * </ul>
   */
  @Override
  public BatchChatResult batchChat(List<BatchChatItem> items) {
    long startTime = System.currentTimeMillis();
    LOG.info("[BatchChat] 批量对话启动: itemsCount={}", items.size());

    // StructuredTaskScope：JDK 21 结构化并发，子线程生命周期严格限定在 try-with-resources 块内
    try (var scope = new StructuredTaskScope<BatchChatResult.BatchResultItem>()) {
      // 为每条请求 fork 一个虚拟线程
      List<StructuredTaskScope.Subtask<BatchChatResult.BatchResultItem>> subtasks = new ArrayList<>(items.size());
      for (BatchChatItem item : items) {
        subtasks.add(scope.fork(() -> executeSingleItem(item)));
      }

      // 阻塞等待所有子线程完成（或失败/取消）
      scope.join();

      // 按原始顺序收集结果
      List<BatchChatResult.BatchResultItem> results = new ArrayList<>(items.size());
      for (StructuredTaskScope.Subtask<BatchChatResult.BatchResultItem> subtask : subtasks) {
        try {
          results.add(subtask.get());
        } catch (Exception e) {
          // subtask 状态异常时（任务失败），构造失败结果
          int idx = subtasks.indexOf(subtask);
          String itemId = items.get(idx).getItemId();
          results.add(BatchChatResult.BatchResultItem.failure(itemId, "获取结果异常: " + e.getMessage()));
        }
      }

      long duration = System.currentTimeMillis() - startTime;
      BatchChatResult result = new BatchChatResult(results, duration);
      LOG.info(
          "[BatchChat] 批量对话完成: total={}, success={}, failed={}, duration={}ms",
          items.size(), result.getSuccessCount(), result.getFailedCount(), duration);
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      long duration = System.currentTimeMillis() - startTime;
      LOG.warn("[BatchChat] 批量对话被中断: duration={}ms", duration);
      // 中断时返回所有条目为失败
      List<BatchChatResult.BatchResultItem> results = new ArrayList<>(items.size());
      for (BatchChatItem item : items) {
        results.add(BatchChatResult.BatchResultItem.failure(item.getItemId(), "批量对话被中断"));
      }
      return new BatchChatResult(results, duration);
    }
  }

  /**
   * 执行单条批量对话条目。
   *
   * <p>根据条目内容类型（纯文本 / 多模态）委托 {@link ChatService} 对应方法。 异常在此处捕获并转为失败结果，不向上传播影响其他条目。
   *
   * @param item 单条对话请求
   * @return 单条对话结果
   */
  private BatchChatResult.BatchResultItem executeSingleItem(BatchChatItem item) {
    try {
      ChatResponse response;
      if (item.getMultimodalContent() != null && !item.getMultimodalContent().isEmpty()) {
        response =
            chatService.chat(
                item.getConversationId(), item.getMultimodalContent(), item.getSystemPrompt());
      } else {
        response =
            chatService.chat(item.getConversationId(), item.getMessage(), item.getSystemPrompt());
      }
      return BatchChatResult.BatchResultItem.success(
          item.getItemId(),
          response.getContent(),
          response.getModel(),
          response.getUsage(),
          response.getFinishReason());
    } catch (Exception e) {
      LOG.warn("[BatchChat] 单条对话失败: itemId={}, error={}", item.getItemId(), e.getMessage());
      return BatchChatResult.BatchResultItem.failure(item.getItemId(), e.getMessage());
    }
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
   * <p>委托 {@link ChatService#stream(String, MessageContent, String, Consumer)} 执行多模态流式对话。
   */
  @Override
  public void stream(
      String conversationId,
      MessageContent multimodalContent,
      String systemPrompt,
      Consumer<ChatChunk> chunkConsumer) {
    chatService.stream(conversationId, multimodalContent, systemPrompt, chunkConsumer);
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
   * <p>委托 {@link AgentFactory#getDefaultExecutor()} 获取默认执行器并执行流式 Agent。
   */
  @Override
  public void executeStream(
      AgentExecutionRequest request,
      Consumer<ChatChunk> chunkConsumer,
      Consumer<DagProgressEvent> progressConsumer) {
    agentFactory.getDefaultExecutor().executeStream(request, chunkConsumer, progressConsumer);
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
