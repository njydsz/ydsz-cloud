package com.njydsz.agent.web.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.agent.api.dto.AgentExecutionRequestDTO;
import com.njydsz.agent.api.dto.BatchChatRequestDTO;
import com.njydsz.agent.api.dto.BatchChatResponseDTO;
import com.njydsz.agent.api.dto.ChatRequestDTO;
import com.njydsz.agent.api.dto.ChatResponseDTO;
import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.model.BatchChatResult;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.MessageContent;
import com.njydsz.agent.server.agent.AgentFacade;
import com.njydsz.agent.server.agent.AgentFacade.BatchChatItem;
import com.njydsz.agent.server.chat.AgentRequestGuard;
import com.njydsz.agent.server.chat.SseExecutor;
import com.njydsz.agent.server.chat.SseExecutor.SseChunk;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;

/**
 * Agent 统一入口 Controller（执行 + 对话 + 历史）
 *
 * <p>P1-2 重构：合并原 {@code ChatController} 至此，形成单入口 API 体系：
 *
 * <ul>
 *   <li>{@code POST /api/v1/agent/execute} - 同步执行 Agent，等待完整响应后返回
 *   <li>{@code POST /api/v1/agent/execute/stream} - SSE 流式执行 Agent，逐 chunk 推送 LLM 响应
 *   <li>{@code POST /api/v1/agent/chat} - 同步对话
 *   <li>{@code POST /api/v1/agent/chat/stream} - SSE 流式对话
 *   <li>{@code GET /api/v1/agent/history} - 获取对话历史
 *   <li>{@code DELETE /api/v1/agent/history} - 清除对话历史
 * </ul>
 *
 * <p>可用模型 / 已注册工具等元数据查询接口见 {@link AgentMetadataController}。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>同步 / 流式双模式执行（流式支持心跳保活和客户端断连检测）
 *   <li>多 LLM Provider 路由（通过 {@link AgentFacade} 统一抽象）
 *   <li>幂等防重（5s TTL）+ 限流（50 QPS）+ 审计日志
 * </ul>
 *
 * <h3>SSE 实现细节</h3>
 *
 * <ul>
 *   <li>使用 {@link SseExecutor} 统一封装心跳保活、虚拟线程、断连检测、cleanup 逻辑
 *   <li>使用虚拟线程承载流式执行，节省线程资源
 *   <li>客户端断开时通过 {@code active} 标志中断执行，节省 LLM Token
 * </ul>
 *
 * <h3>架构位置</h3>
 *
 * <pre>
 *   前端 Chat UI / Agent 调用方
 *     → ydsz-gateway
 *       → ydsz-agent-web（本 Controller）
 *         → ydsz-agent-server.AgentFacade（应用门面）
 *           → ChatService / AgentFactory
 *             → LlmClient（OpenAI / Claude / 通义千问 / 文心一言 ...）
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AgentMetadataController 元数据查询接口（可用模型 / 已注册工具）
 * @see AgentFacade Agent 应用门面
 * @see AgentRequestGuard 请求守卫（幂等 + 限流 + 业务校验）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "Agent 统一入口", description = "Agent 执行 / 对话 / 历史")
public class AgentController {

  /** Agent 应用门面（解耦 Controller 与内部服务） */
  private final AgentFacade agentFacade;

  /** 请求守卫（幂等 + 限流 + 业务校验） */
  private final AgentRequestGuard requestGuard;

  /**
   * 同步执行 Agent，等待完整响应后返回。
   *
   * <p>适用于非实时对话场景（自动化任务、批处理等），由 {@link AgentFactory#getDefaultExecutor()} 获取默认执行器并执行；执行异常时主动调用
   * {@link AgentRequestGuard#releaseIdempotent} 释放幂等锁， 避免请求失败后 5 秒内重试被误判为重复。
   *
   * @param request Agent 执行请求体（含 agentCode / userInput / systemPrompt / maxIterations /
   *     enabledTools）
   * @return 统一响应结果，data 为 {@link ChatResponseDTO}（含 content/model/usage）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_EXECUTE)
  @Audit(
      module = "Agent管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'execute'")
  @Idempotent(key = "'agent:execute:' + #request.requestId", ttlSeconds = 5)
  @RateLimit(resource = "agent.agent.execute", threshold = 50)
  @PostMapping("/execute")
  @Operation(summary = "同步执行 Agent", description = "等待完整响应后返回，适用于非实时对话场景")
  public YdszResponse<ChatResponseDTO> execute(
      @Valid @RequestBody AgentExecutionRequestDTO request) {
    log.info(
        "[Agent-API] 执行请求: agentCode={}, stream={}", request.getAgentCode(), request.isStream());
    requestGuard.check(request.getRequestId(), null);
    AgentExecutionRequest execReq = toExecutionRequest(request);
    try {
      ChatResponse response = agentFacade.execute(execReq);
      return YdszResponse.success(toDTO(response));
    } catch (Exception e) {
      requestGuard.releaseIdempotent(request.getRequestId());
      throw e;
    }
  }

  /**
   * 流式执行 Agent（SSE 实时推送）。
   *
   * <p>基于 Server-Sent Events 逐 chunk 推送 LLM 响应内容，适合实时对话和长文本生成场景。
   *
   * <p>实现细节：
   *
   * <ul>
   *   <li>使用 {@link SseExecutor} 统一封装心跳保活、虚拟线程、断连检测、cleanup 逻辑
   *   <li>每 15 秒发送 {@code keep-alive} 注释帧保活，防止中间代理断连
   *   <li>使用虚拟线程承载 LLM 调用，节省线程资源
   *   <li>客户端断开后通过 {@code active} 标志终止 LLM 调用，节省 Token 成本
   *   <li>事件类型：{@code chunk}（增量内容）/ {@code done}（正常结束）/ {@code error}（异常结束）
   * </ul>
   *
   * @param request Agent 执行请求体
   * @return SseEmitter（Spring MVC 的 SSE 句柄）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_EXECUTE)
  @Audit(
      module = "Agent管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'executeStream'")
  @Idempotent(key = "'agent:execute:stream:' + #request.requestId", ttlSeconds = 5)
  @RateLimit(resource = "agent.agent.executeStream", threshold = 50)
  @PostMapping(value = "/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(summary = "流式执行 Agent（SSE）", description = "逐 chunk 推送 LLM 响应，支持心跳保活和断连检测")
  public SseEmitter executeStream(@Valid @RequestBody AgentExecutionRequestDTO request) {
    log.info("[Agent-API] 流式执行请求: agentCode={}", request.getAgentCode());
    requestGuard.check(request.getRequestId(), null);
    SseEmitter emitter = new SseEmitter();
    SseExecutor executor = new SseExecutor(emitter);
    AgentExecutionRequest execReq = toExecutionRequest(request);

    executor.execute(
        chunkConsumer ->
            agentFacade.executeStream(
                execReq,
                chunk -> {
                  if (chunk.hasContent()) {
                    chunkConsumer.accept(
                        SseChunk.content(
                            chunk.getDeltaContent(),
                            chunk.getFinishReason(),
                            chunk.getDeltaToolCalls()));
                  } else if (chunk.isFinished()) {
                    chunkConsumer.accept(SseChunk.finish(chunk.getFinishReason()));
                  } else {
                    // 工具调用等非文本 chunk 原样转发
                    chunkConsumer.accept(
                        SseChunk.content(
                            chunk.getDeltaContent(),
                            chunk.getFinishReason(),
                            chunk.getDeltaToolCalls()));
                  }
                },
                // P2-#14: 进度回调 — 将 DAG 节点事件转为 SSE progress 事件推送
                progressEvent -> {
                  if (progressEvent == null) {
                    return;
                  }
                  String status = progressEvent.getEventType();
                  String nodeId = progressEvent.getNodeId();
                  int completed = progressEvent.getCompletedCount();
                  int total = progressEvent.getTotalCount();
                  try {
                    emitter.send(
                        SseEmitter.event()
                            .data(
                                String.format(
                                    "{\"eventType\":\"%s\",\"nodeId\":\"%s\",\"nodeType\":\"%s\","
                                        + "\"completedCount\":%d,\"totalCount\":%d,\"error\":\"%s\"}",
                                    status,
                                    nodeId != null ? nodeId : "",
                                    progressEvent.getNodeType() != null ? progressEvent.getNodeType() : "",
                                    completed,
                                    total,
                                    progressEvent.getError() != null ? progressEvent.getError() : ""))
                            .name("progress"));
                  } catch (IOException e) {
                    log.debug("[Agent-API] SSE progress 发送失败（客户端已断开）: {}", e.getMessage());
                  }
                }));

    return emitter;
  }

  // ==========================================================================
  // P1-2: 对话接口（从 ChatController 合并）
  // ==========================================================================

  /**
   * 同步对话。
   *
   * <p>等待 LLM 返回完整响应后返回，适用于非实时对话场景（自动化问答、批处理对话等）。
   *
   * @param request 对话请求体（含 conversationId / message / systemPrompt / requestId）
   * @return 统一响应结果，data 为 {@link ChatResponseDTO}（含 content/model/usage）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_CHAT)
  @Audit(
      module = "对话管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'chat'")
  @Idempotent(key = "'agent:chat:' + #request.requestId", ttlSeconds = 5)
  @RateLimit(resource = "agent.chat.chat", threshold = 50)
  @PostMapping("/chat")
  @Operation(summary = "同步对话", description = "等待 LLM 返回完整响应后返回")
  public YdszResponse<ChatResponseDTO> chat(@Valid @RequestBody ChatRequestDTO request) {
    requestGuard.check(request.getRequestId(), null);
    try {
      ChatResponse response;
      // 多模态输入优先：multimodalContent 非空时使用 Vision 模型对话
      if (request.getMultimodalContent() != null && !request.getMultimodalContent().isEmpty()) {
        log.info(
            "[Chat-API] 多模态同步对话请求: convId={}, partsCount={}",
            request.getConversationId(),
            request.getMultimodalContent().size());
        MessageContent content = toMessageContent(request.getMultimodalContent());
        response = agentFacade.chat(request.getConversationId(), content, request.getSystemPrompt());
      } else {
        log.info(
            "[Chat-API] 同步对话请求: convId={}, msgLen={}",
            request.getConversationId(),
            request.getMessage().length());
        response =
            agentFacade.chat(
                request.getConversationId(), request.getMessage(), request.getSystemPrompt());
      }
      ChatResponseDTO dto = toDTO(response);
      return YdszResponse.success(dto);
    } catch (Exception e) {
      requestGuard.releaseIdempotent(request.getRequestId());
      throw e;
    }
  }

  /**
   * 流式对话（SSE 实时推送）。
   *
   * <p>基于 Server-Sent Events 逐 token 推送 LLM 响应内容，适用于实时聊天场景。
   *
   * @param request 对话请求体
   * @return SseEmitter（Spring MVC 的 SSE 句柄）
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_CHAT)
  @Audit(
      module = "对话管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'chatStream'")
  @Idempotent(key = "'agent:chat:stream:' + #request.requestId", ttlSeconds = 5)
  @RateLimit(resource = "agent.chat.chatStream", threshold = 50)
  @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(summary = "流式对话（SSE）", description = "逐 token 推送 LLM 响应")
  public SseEmitter chatStream(@Valid @RequestBody ChatRequestDTO request) {
    requestGuard.check(request.getRequestId(), null);
    SseEmitter emitter = new SseEmitter();
    SseExecutor executor = new SseExecutor(emitter);

    // 多模态输入优先：multimodalContent 非空时使用 Vision 模型流式对话
    if (request.getMultimodalContent() != null && !request.getMultimodalContent().isEmpty()) {
      log.info(
          "[Chat-API] 多模态流式对话请求: convId={}, partsCount={}",
          request.getConversationId(),
          request.getMultimodalContent().size());
      MessageContent content = toMessageContent(request.getMultimodalContent());
      executor.execute(
          chunkConsumer ->
              agentFacade.stream(
                  request.getConversationId(),
                  content,
                  request.getSystemPrompt(),
                  chunk -> {
                    if (chunk.hasContent()) {
                      chunkConsumer.accept(
                          SseChunk.content(
                              chunk.getDeltaContent(),
                              chunk.getFinishReason(),
                              chunk.getDeltaToolCalls()));
                    } else if (chunk.isFinished()) {
                      chunkConsumer.accept(SseChunk.finish(chunk.getFinishReason()));
                    } else {
                      // 工具调用等非文本 chunk 原样转发
                      chunkConsumer.accept(
                          SseChunk.content(
                              chunk.getDeltaContent(),
                              chunk.getFinishReason(),
                              chunk.getDeltaToolCalls()));
                    }
                  }));
      return emitter;
    }

    log.info("[Chat-API] 流式对话请求: convId={}", request.getConversationId());
    executor.execute(
        chunkConsumer ->
            agentFacade.stream(
                request.getConversationId(),
                request.getMessage(),
                request.getSystemPrompt(),
                chunk -> {
                  if (chunk.hasContent()) {
                    chunkConsumer.accept(
                        SseChunk.content(
                            chunk.getDeltaContent(),
                            chunk.getFinishReason(),
                            chunk.getDeltaToolCalls()));
                  } else if (chunk.isFinished()) {
                    chunkConsumer.accept(SseChunk.finish(chunk.getFinishReason()));
                  } else {
                    // 工具调用等非文本 chunk 原样转发
                    chunkConsumer.accept(
                        SseChunk.content(
                            chunk.getDeltaContent(),
                            chunk.getFinishReason(),
                            chunk.getDeltaToolCalls()));
                  }
                }));

    return emitter;
  }

  /**
   * 批量对话（并行执行）。
   *
   * <p>使用 JDK 21 结构化并发并行处理多条对话请求，单条失败不影响其他条目。 适用于批量问答、多 Prompt 对比测试、A/B 评估等场景。
   *
   * <p>限制：
   *
   * <ul>
   *   <li>单次最多 50 条
   *   <li>所有请求共享同一模型配置（model / temperature / maxTokens）
   *   <li>每条请求独立对话 ID，互不干扰
   * </ul>
   *
   * @param request 批量对话请求体
   * @return 统一响应结果，data 为 {@link BatchChatResponseDTO}
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_CHAT)
  @Audit(
      module = "对话管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'batchChat'")
  @Idempotent(key = "'agent:chat:batch:' + #request.requestId", ttlSeconds = 300)
  @RateLimit(resource = "agent.chat.batch", threshold = 10)
  @PostMapping("/chat/batch")
  @Operation(summary = "批量对话（并行）", description = "并行处理多条对话请求，单条失败不影响其他条目")
  public YdszResponse<BatchChatResponseDTO> batchChat(
      @Valid @RequestBody BatchChatRequestDTO request) {
    log.info("[Batch-API] 批量对话请求: itemsCount={}", request.getItems().size());
    requestGuard.check(request.getRequestId(), null);
    try {
      // DTO → 应用层 BatchChatItem 转换
      List<BatchChatItem> facadeItems = new ArrayList<>(request.getItems().size());
      for (BatchChatRequestDTO.BatchChatItem dto : request.getItems()) {
        MessageContent content = null;
        if (dto.getMultimodalContent() != null && !dto.getMultimodalContent().isEmpty()) {
          content = toMessageContent(dto.getMultimodalContent());
        }
        facadeItems.add(
            new BatchChatItem(
                dto.getItemId(),
                dto.getConversationId(),
                dto.getMessage(),
                content,
                dto.getSystemPrompt()));
      }
      BatchChatResult result = agentFacade.batchChat(facadeItems);
      return YdszResponse.success(toBatchDTO(result));
    } catch (Exception e) {
      requestGuard.releaseIdempotent(request.getRequestId());
      throw e;
    }
  }

  /**
   * 获取指定 conversationId 的对话历史。
   *
   * @param conversationId 会话 ID
   * @return 统一响应结果，data 为对话消息列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_CHAT)
  @Audit(
      module = "对话管理",
      type = AuditType.OPERATION,
      action = AuditAction.QUERY,
      content = "'history: ' + #conversationId")
  @GetMapping("/history")
  @Operation(summary = "获取对话历史")
  public YdszResponse<List<Map<String, Object>>> history(@RequestParam String conversationId) {
    List<ChatMessage> messages = agentFacade.getHistory(conversationId);
    List<Map<String, Object>> result = new ArrayList<>();
    for (ChatMessage msg : messages) {
      result.add(
          Map.of(
              "id",
              msg.getId(),
              "role",
              msg.getRole().getApiValue(),
              "content",
              msg.getContent() != null ? msg.getContent() : "",
              "createdAt",
              msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : ""));
    }
    return YdszResponse.success(result);
  }

  /**
   * 清除指定 conversationId 的对话历史。
   *
   * @param conversationId 会话 ID
   * @return 统一响应结果
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_CHAT)
  @Audit(
      module = "对话管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'clearHistory'")
  @Idempotent(key = "'agent:chat:clear:' + #requestId", ttlSeconds = 5)
  @RateLimit(resource = "agent.chat.clearHistory", threshold = 50)
  @DeleteMapping("/history")
  @Operation(summary = "清除对话历史")
  public YdszResponse<Void> clearHistory(
      @RequestParam String conversationId, @RequestParam(required = false) String requestId) {
    agentFacade.clearHistory(conversationId);
    return YdszResponse.success();
  }

  // ==========================================================================
  // 内部方法
  // ==========================================================================

  /** BatchChatResult → BatchChatResponseDTO 转换。 */
  private BatchChatResponseDTO toBatchDTO(BatchChatResult result) {
    BatchChatResponseDTO dto = new BatchChatResponseDTO();
    dto.setTotalDurationMs(result.getTotalDurationMs());
    dto.setSuccessCount(result.getSuccessCount());
    dto.setFailedCount(result.getFailedCount());

    List<BatchChatResponseDTO.BatchResultItem> itemDTOs = new ArrayList<>(result.getResults().size());
    for (BatchChatResult.BatchResultItem item : result.getResults()) {
      BatchChatResponseDTO.BatchResultItem itemDTO = new BatchChatResponseDTO.BatchResultItem();
      itemDTO.setItemId(item.getItemId());
      itemDTO.setSuccess(item.isSuccess());
      itemDTO.setContent(item.getContent());
      itemDTO.setModel(item.getModel());
      itemDTO.setFinishReason(item.getFinishReason());
      itemDTO.setErrorMessage(item.getErrorMessage());
      if (item.getUsage() != null) {
        itemDTO.setUsage(
            new ChatResponseDTO.TokenUsageDTO(
                item.getUsage().getPromptTokens(),
                item.getUsage().getCompletionTokens(),
                item.getUsage().getTotalTokens()));
      }
      itemDTOs.add(itemDTO);
    }
    dto.setResults(itemDTOs);
    return dto;
  }

  /** ChatResponse → ChatResponseDTO 转换（Agent 执行用）。 */
  private AgentExecutionRequest toExecutionRequest(AgentExecutionRequestDTO dto) {
    return AgentExecutionRequest.builder()
        .conversationId(dto.getConversationId())
        .userInput(dto.getUserInput())
        .systemPrompt(dto.getSystemPrompt())
        .maxIterations(dto.getMaxIterations() != null ? dto.getMaxIterations() : 10)
        .enabledTools(dto.getEnabledTools())
        .build();
  }

  /** ChatResponse → DTO 转换。 */
  private ChatResponseDTO toDTO(ChatResponse response) {
    ChatResponseDTO dto = new ChatResponseDTO();
    dto.setContent(response.getContent());
    dto.setModel(response.getModel());
    dto.setRespondedAt(LocalDateTime.now());
    if (response.getUsage() != null) {
      dto.setUsage(
          new ChatResponseDTO.TokenUsageDTO(
              response.getUsage().getPromptTokens(),
              response.getUsage().getCompletionTokens(),
              response.getUsage().getTotalTokens()));
    }
    return dto;
  }

  /**
   * 将 DTO 列表转换为领域值对象 {@link MessageContent}。
   *
   * <p>每个 {@link ChatRequestDTO.ContentPartDTO} 转换为 {@link MessageContent.ContentPart}，保留类型与内容。
   *
   * @param dtos DTO 段落列表
   * @return 多模态内容值对象
   */
  private MessageContent toMessageContent(List<ChatRequestDTO.ContentPartDTO> dtos) {
    List<MessageContent.ContentPart> parts = new ArrayList<>(dtos.size());
    for (ChatRequestDTO.ContentPartDTO dto : dtos) {
      Objects.requireNonNull(dto.getType(), "ContentPart type 不能为 null");
      if ("text".equals(dto.getType())) {
        parts.add(MessageContent.ContentPart.text(dto.getText()));
      } else if ("image_url".equals(dto.getType())) {
        parts.add(MessageContent.ContentPart.image(dto.getImageUrl()));
      } else {
        throw new IllegalArgumentException("不支持的内容类型: " + dto.getType());
      }
    }
    return new MessageContent(parts);
  }
}
