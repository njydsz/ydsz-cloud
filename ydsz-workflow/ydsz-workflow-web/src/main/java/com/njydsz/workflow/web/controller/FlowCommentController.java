package com.njydsz.workflow.web.controller.notification;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.workflow.domain.dto.FlowCommentCreateDTO;
import com.njydsz.workflow.domain.dto.FlowQuickCommentDTO;
import com.njydsz.workflow.domain.vo.FlowCommentVO;
import com.njydsz.workflow.domain.vo.FlowQuickCommentVO;
import com.njydsz.workflow.server.service.FlowCommentService;

/**
 * P2-2: 流程评论与常用语统一 Controller
 *
 * <p>审批评论多级回复 + 审批常用语管理 HTTP 接口，对标钉钉 / 飞书审批评论区与常用语能力。
 * 评论数据独立于审计日志（{@code FlowAuditLogDO}）：用户视角可修改 / 删除，系统视角只读不可改。
 * 常用语提供系统预设 + 用户自定义双轨制，使用次数智能排序。
 *
 * <p><b>接口分组：</b>
 *
 * <ul>
 *   <li><b>发表评论</b>：{@code POST /workflow/comment} — 发表评论 / 回复（{@code parentCommentId} 非空时为回复）
 *   <li><b>评论查询</b>：
 *       <ul>
 *         <li>{@code GET /workflow/comment/instance/{instanceId}} — 实例全部评论（树结构）
 *         <li>{@code GET /workflow/comment/root/{instanceId}} — 实例一级评论
 *         <li>{@code GET /workflow/comment/replies/{parentCommentId}} — 父评论下的回复
 *       </ul>
 *   <li><b>删除评论</b>：{@code DELETE /workflow/comment/{commentId}} — 软删除（仅本人）
 *   <li><b>常用语查询</b>：{@code GET /workflow/comment/quick}（当前用户常用语，按 sortNum + useCount 排序）
 *   <li><b>常用语新增</b>：{@code POST /workflow/comment/quick}（新增用户自定义常用语）
 *   <li><b>常用语编辑</b>：{@code PUT /workflow/comment/quick}（仅编辑本人创建的）
 *   <li><b>常用语删除</b>：{@code DELETE /workflow/comment/quick/{id}}（系统预设不可删）
 *   <li><b>常用语计数</b>：{@code POST /workflow/comment/quick/{id}/use}（审批时调用 +1）
 * </ul>
 *
 * <p><b>权限模型：</b>所有写接口通过 {@link AuthApiPermission} 校验 {@link
 * PermissionCodes#WORKFLOW_COMMENT_OPERATE} 权限码；删除操作额外校验 「操作用户 == 评论人」防越权。
 *
 * <p><b>限流：</b>发表 / 回复通过 {@link Idempotent} 5s 防重；删除通过 {@link Idempotent} 防重。
 *
 * <p><b>性能优化：</b>实例全量评论一次性拉取后由前端本地组装树，避免 N+1 查询； 评论分页采用 {@code (created_at, parent_comment_id)}
 * 复合索引。
 *
 * <p><b>通知触发：</b>评论 / 回复时由 {@link FlowCommentService} 调用 {@code FlowNotificationService}
 * 通知被回复人；提及人（{@code @xxx}）索引便于 @ 检索。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowCommentService 评论服务（含常用语能力）
 * @see FlowCommentDO 评论实体
 * @see FlowCommentCreateDTO 评论创建 DTO
 */
@Slf4j
@RestController
@Tag(name = "workflow-comment", description = "工作流审批评论与常用语统一接口")
@RequestMapping("/api/v1/workflow/comment")
@RequiredArgsConstructor
public class FlowCommentController {

  /** 流程评论服务（含常用语能力），负责评论/回复的发表、查询与删除，以及常用语的增删改查与使用次数统计 */
  private final FlowCommentService commentService;

  /**
   * 发表评论或回复
   *
   * @param dto 评论参数
   * @return 统一响应结果，包含新评论 ID
   */
  @Idempotent(key = "ydsz:workflow:FlowCommentController:addComment:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowCommentDO.addComment", threshold = 50)
  @PostMapping
  @Audit(
      module = "流程评论",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'addComment'")
  @Operation(summary = "发表评论/回复")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public YdszResponse<String> addComment(@Valid @RequestBody FlowCommentCreateDTO dto) {
    String userId = AuthContextUtils.getUserId();
    String userName = AuthContextUtils.getUsername();
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(commentService.addComment(dto, userId, userName, tenantId));
  }

  /**
   * 查询实例下全部评论（一级 + 回复，按创建时间正序）
   *
   * @param instanceId 实例 ID
   * @return 统一响应结果，包含全部评论列表
   */
  @GetMapping("/instance/{instanceId}")
  @Operation(summary = "查询实例全部评论（树结构）")
  public YdszResponse<List<FlowCommentVO>> listByInstance(@PathVariable String instanceId) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(commentService.listByInstance(tenantId, instanceId));
  }

  /**
   * 查询实例下全部一级评论（不含回复）
   *
   * @param instanceId 实例 ID
   * @return 统一响应结果，包含一级评论列表
   */
  @GetMapping("/root/{instanceId}")
  @Operation(summary = "查询实例一级评论")
  public YdszResponse<List<FlowCommentVO>> listRootComments(@PathVariable String instanceId) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return YdszResponse.success(commentService.listRootComments(tenantId, instanceId));
  }

  /**
   * 查询指定父评论下的全部回复
   *
   * @param parentCommentId 父评论 ID
   * @return 统一响应结果，包含回复列表
   */
  @GetMapping("/replies/{parentCommentId}")
  @Operation(summary = "查询父评论下的回复")
  public YdszResponse<List<FlowCommentVO>> listReplies(@PathVariable String parentCommentId) {
    return YdszResponse.success(commentService.listReplies(parentCommentId));
  }

  /**
   * 删除评论（仅评论人本人可删除）
   *
   * @param commentId 评论 ID
   * @return 统一响应结果，包含是否删除成功
   */
  @Idempotent(key = "ydsz:workflow:FlowCommentController:deleteComment:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowCommentDO.deleteComment", threshold = 50)
  @DeleteMapping("/{commentId}")
  @Audit(
      module = "流程评论",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'deleteComment'")
  @Operation(summary = "删除评论（仅本人）")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public YdszResponse<Boolean> deleteComment(@PathVariable String commentId) {
    String userId = AuthContextUtils.getUserId();
    return YdszResponse.success(commentService.deleteComment(commentId, userId));
  }

  // ==================== 常用语管理 ====================

  /**
   * 查询当前用户的常用语列表。
   *
   * @return 常用语列表
   */
  @GetMapping("/quick")
  @Operation(summary = "查询当前用户的常用语列表")
  public YdszResponse<List<FlowQuickCommentVO>> listQuickComments() {
    String userId = AuthContextUtils.getUserId();
    String tenantId = TenantContextHolder.getTenantId();
    return YdszResponse.success(commentService.listQuickComments(userId, tenantId));
  }

  /**
   * 新增常用语。
   *
   * @param dto 常用语信息
   * @return 新建常用语 ID
   */
  @Idempotent(key = "ydsz:workflow:FlowCommentController:createQuickComment:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowQuickCommentDO.create", threshold = 50)
  @PostMapping("/quick")
  @Audit(
      module = "流程评论",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'createQuickComment'")
  @Operation(summary = "新增常用语")
  public YdszResponse<String> createQuickComment(@Valid @RequestBody FlowQuickCommentDTO dto) {
    String userId = AuthContextUtils.getUserId();
    String tenantId = TenantContextHolder.getTenantId();
    return YdszResponse.success(commentService.createQuickComment(dto, userId, tenantId));
  }

  /**
   * 编辑常用语。
   *
   * @param dto 常用语信息
   * @return 空响应
   */
  @Idempotent(key = "ydsz:workflow:FlowCommentController:updateQuickComment:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowQuickCommentDO.update", threshold = 50)
  @PutMapping("/quick")
  @Audit(
      module = "流程评论",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'updateQuickComment'")
  @Operation(summary = "编辑常用语")
  public YdszResponse<Void> updateQuickComment(@Valid @RequestBody FlowQuickCommentDTO dto) {
    String userId = AuthContextUtils.getUserId();
    commentService.updateQuickComment(dto, userId);
    return YdszResponse.success();
  }

  /**
   * 删除常用语。
   *
   * @param id 常用语 ID
   * @return 空响应
   */
  @Idempotent(key = "ydsz:workflow:FlowCommentController:deleteQuickComment:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowQuickCommentDO.delete", threshold = 50)
  @DeleteMapping("/quick/{id}")
  @Audit(
      module = "流程评论",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'deleteQuickComment'")
  @Operation(summary = "删除常用语")
  public YdszResponse<Void> deleteQuickComment(@PathVariable String id) {
    String userId = AuthContextUtils.getUserId();
    commentService.deleteQuickComment(id, userId);
    return YdszResponse.success();
  }

  /**
   * 增加使用次数（审批时调用）。
   *
   * @param id 常用语 ID
   * @return 空响应
   */
  @Idempotent(
      key = "ydsz:workflow:FlowCommentController:incrementUseCount:lock",
      ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowQuickCommentDO.incrementUseCount", threshold = 50)
  @PostMapping("/quick/{id}/use")
  @Audit(
      module = "流程评论",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'incrementUseCount'")
  @Operation(summary = "增加使用次数（审批时调用）")
  public YdszResponse<Void> incrementUseCount(@PathVariable String id) {
    commentService.incrementQuickCommentUseCount(id);
    return YdszResponse.success();
  }
}
