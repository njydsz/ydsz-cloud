package com.njydsz.agent.web.controller;

import java.util.List;
import java.util.Objects;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.dto.ConsolidateMemoryRequest;
import com.njydsz.agent.domain.dto.SaveMemoryRequest;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.MessageRole;
import com.njydsz.agent.domain.vo.MemoryVO;
import com.njydsz.agent.server.memory.ConversationMemoryConsolidationService;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;

/**
 * 对话记忆管理控制器。
 *
 * <p>提供 Agent 对话历史的查询、写入、清除、整合等 HTTP 接口。 底层委托 {@link ConversationMemory} 实现滑动窗口存储，整合功能由
 * {@link ConversationMemoryConsolidationService} 编排 LLM 事实提取与画像刷新。
 *
 * <h3>接口概览</h3>
 *
 * <ul>
 *   <li>GET /api/agent/memory/{conversationId} — 加载对话记忆
 *   <li>POST /api/agent/memory/{conversationId} — 写入单条记忆
 *   <li>DELETE /api/agent/memory/{conversationId} — 清除对话记忆
 *   <li>GET /api/agent/memory/{conversationId}/count — 获取消息数量
 *   <li>POST /api/agent/memory/{conversationId}/consolidate — 触发记忆整合（提取事实）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Slf4j
@ApiVersion("26.09.13")
@RestController
@RequestMapping("/agent/memory")
@RequiredArgsConstructor
@Validated
@Tag(name = "对话记忆管理", description = "Agent 对话历史的查询、写入、清除、整合")
public class MemoryController {

  private final ConversationMemory conversationMemory;
  private final ConversationMemoryConsolidationService consolidationService;

  /**
   * 加载对话历史记忆。
   *
   * @param conversationId 对话 ID
   * @param maxMessages 最大返回消息数（默认 50，上限 200）
   * @return 历史消息列表（按时间正序）
   */
  @GetMapping("/{conversationId}")
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_CHAT)
  @Operation(summary = "加载对话记忆", description = "按对话 ID 加载历史消息，支持滑动窗口限制返回数量")
  public YdszResponse<List<MemoryVO>> loadMemory(
      @PathVariable("conversationId") String conversationId,
          @RequestParam(value = "maxMessages", defaultValue = "50") @Min(1) @Max(200) int maxMessages) {
    List<ChatMessage> messages = conversationMemory.load(conversationId, maxMessages);
    List<MemoryVO> voList = messages.stream().map(this::toMemoryVo).toList();
    log.info("加载对话记忆: conversationId={}, count={}", conversationId, voList.size());
    return YdszResponse.success(voList);
  }

  /**
   * 写入单条对话记忆。
   *
   * @param conversationId 对话 ID
   * @param request 保存请求（角色、内容、可选 toolCallId）
   * @return 写入成功返回空
   */
  @PostMapping("/{conversationId}")
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_CHAT)
  @Operation(summary = "写入对话记忆", description = "向指定对话写入单条消息记忆")
  public YdszResponse<Void> saveMemory(
      @PathVariable("conversationId") String conversationId, @Valid @RequestBody SaveMemoryRequest request) {
    MessageRole role = MessageRole.fromApiValue(request.getRole());
    ChatMessage message =
        switch (role) {
          case SYSTEM -> ChatMessage.system(request.getContent());
          case USER -> ChatMessage.user(request.getContent(), conversationId);
          case ASSISTANT -> ChatMessage.assistant(request.getContent(), conversationId, null);
          case TOOL -> ChatMessage.tool(
              Objects.requireNonNull(request.getToolCallId(), "toolCallId 不能为空"),
              request.getContent(),
              conversationId);
        };
    conversationMemory.save(conversationId, message);
    log.info("写入对话记忆: conversationId={}, role={}", conversationId, role);
    return YdszResponse.success();
  }

  /**
   * 清除对话历史记忆。
   *
   * @param conversationId 对话 ID
   * @return 清除成功返回空
   */
  @DeleteMapping("/{conversationId}")
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_CHAT)
  @Operation(summary = "清除对话记忆", description = "清除指定对话的全部历史记忆")
  public YdszResponse<Void> clearMemory(@PathVariable("conversationId") String conversationId) {
    conversationMemory.clear(conversationId);
    log.info("清除对话记忆: conversationId={}", conversationId);
    return YdszResponse.success();
  }

  /**
   * 获取对话消息数量。
   *
   * @param conversationId 对话 ID
   * @return 消息数量
   */
  @GetMapping("/{conversationId}/count")
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_CHAT)
  @Operation(summary = "获取消息数量", description = "获取指定对话的消息总条数")
  public YdszResponse<Long> countMessages(@PathVariable("conversationId") String conversationId) {
    long count = conversationMemory.count(conversationId);
    log.debug("查询对话消息数量: conversationId={}, count={}", conversationId, count);
    return YdszResponse.success(count);
  }

  /**
   * 触发记忆整合。
   *
   * <p>对指定对话的历史执行事实提取、画像刷新等整合操作。
   *
   * @param conversationId 对话 ID
   * @param request 整合请求（可选租户 ID）
   * @return 触发成功返回空
   */
  @PostMapping("/{conversationId}/consolidate")
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_EXECUTE)
  @Operation(summary = "触发记忆整合", description = "提取对话中有价值的事实并刷新用户画像")
  public YdszResponse<Void> consolidate(
      @PathVariable("conversationId") String conversationId,
      @RequestBody ConsolidateMemoryRequest request) {
    String tenantId = request.getTenantId();
    consolidationService.consolidateConversation(conversationId, tenantId);
    log.info("触发记忆整合: conversationId={}, tenantId={}", conversationId, tenantId);
    return YdszResponse.success();
  }

  /**
   * 将 ChatMessage 转换为 MemoryVO。
   *
   * @param message 对话消息
   * @return 记忆视图对象
   */
  private MemoryVO toMemoryVo(ChatMessage message) {
    MemoryVO vo = new MemoryVO();
    vo.setId(message.getId());
    vo.setRole(message.getRole().getApiValue());
    vo.setContent(message.getContent());
    vo.setConversationId(message.getConversationId());
    vo.setCreatedAt(message.getCreatedAt());
    vo.setToolCallId(message.getToolCallId());
    return vo;
  }
}
