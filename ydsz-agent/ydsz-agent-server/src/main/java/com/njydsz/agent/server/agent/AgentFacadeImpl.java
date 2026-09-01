package com.njydsz.agent.server.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.DagProgressEvent;
import com.njydsz.agent.domain.model.BatchChatResult;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.MessageContent;
import com.njydsz.agent.server.chat.ChatService;
import com.njydsz.common.thread.util.ExecutorUtils;

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

  /** Agent 定义服务（按 code 查找定义以支持类型路由） */
  private final AgentDefinitionService agentDefinitionService;

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
   * <p>使用 JDK 21 虚拟线程 + {@link CompletableFuture} 并行处理多条对话请求：
   *
   * <ul>
   *   <li>每条请求在独立虚拟线程中执行，单条失败不影响其他条目
   *   <li>结果顺序与请求 items 顺序一致
   *   <li>总耗时取所有线程中最长者（并行加速）
   *   <li>使用守护线程池，JVM 退出时自动回收
   * </ul>
   */
  @Override
  public BatchChatResult batchChat(List<BatchChatItem> items) {
    long startTime = System.currentTimeMillis();
    log.info("[BatchChat] 批量对话启动: itemsCount={}", items.size());

    // 虚拟线程池：每个任务一个虚拟线程，适合 I/O 密集型（LLM HTTP 调用）
    ExecutorService executor = ExecutorUtils.newVirtualThreadExecutor("agent-facade-batch-");
    try {
      // 为每条请求提交一个异步任务
      List<CompletableFuture<BatchChatResult.BatchResultItem>> futures = new ArrayList<>(items.size());
      for (BatchChatItem item : items) {
        futures.add(CompletableFuture.supplyAsync(() -> executeSingleItem(item), executor));
      }

      // 阻塞等待所有任务完成（或失败）
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

      // 按原始顺序收集结果
      List<BatchChatResult.BatchResultItem> results = new ArrayList<>(items.size());
      for (int i = 0; i < futures.size(); i++) {
        try {
          results.add(futures.get(i).get());
        } catch (Exception e) {
          // 单个任务失败时构造失败结果，不影响其他条目
          String itemId = items.get(i).getItemId();
          results.add(BatchChatResult.BatchResultItem.failure(itemId, "获取结果异常: " + e.getMessage()));
        }
      }

      long duration = System.currentTimeMillis() - startTime;
      BatchChatResult result = new BatchChatResult(results, duration);
      log.info(
          "[BatchChat] 批量对话完成: total={}, success={}, failed={}, duration={}ms",
          items.size(), result.getSuccessCount(), result.getFailedCount(), duration);
      return result;
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      log.warn("[BatchChat] 批量对话异常: duration={}ms, error={}", duration, e.getMessage());
      // 异常时返回所有条目为失败
      List<BatchChatResult.BatchResultItem> results = new ArrayList<>(items.size());
      for (BatchChatItem item : items) {
        results.add(BatchChatResult.BatchResultItem.failure(item.getItemId(), "批量对话异常: " + e.getMessage()));
      }
      return new BatchChatResult(results, duration);
    } finally {
      executor.shutdown();
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
      log.warn("[BatchChat] 单条对话失败: itemId={}, error={}", item.getItemId(), e.getMessage());
      return BatchChatResult.BatchResultItem.failure(item.getItemId(), e.getMessage());
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>根据请求中的 agentCode 查找 Agent 定义，路由到对应类型的执行器。
   * 若未找到定义则降级使用默认执行器（ReAct）。
   */
  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    if (request.getAgentCode() != null && agentDefinitionService != null) {
      AgentDefinition definition = agentDefinitionService.getByCode(request.getAgentCode());
      if (definition != null) {
        log.debug("[AgentFacade] 路由到类型执行器: agentCode={}, type={}",
            request.getAgentCode(), definition.getType());
        return agentFactory.getExecutor(definition).execute(request);
      }
    }
    log.debug("[AgentFacade] 使用默认执行器: agentCode={}", request.getAgentCode());
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
