package com.remisoft.workflow.web.controller.notification;

import java.util.List;

import com.remisoft.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.remisoft.common.auth.annotation.AuthApiPermission;
import com.remisoft.common.auth.context.AuthContextUtils;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.common.permission.PermissionCodes;
import com.remisoft.workflow.domain.dto.FlowCommentCreateDTO;
import com.remisoft.workflow.domain.entity.FlowComment;
import com.remisoft.workflow.server.service.FlowCommentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.remisoft.workflow.domain.converter.WorkflowConverter;
import com.remisoft.workflow.domain.vo.FlowCommentVO;

import com.remisoft.common.audit.annotation.Audit;
import com.remisoft.common.audit.enums.AuditAction;
import com.remisoft.common.audit.enums.AuditType;
/**
 * P2-2: 流程评论 Controller
 *
 * <p>审批评论多级回复 HTTP 接口，对标钉钉 / 飞书审批评论区。
 * 评论数据独立于审计日志（{@code FlowAuditLog}）：用户视角可修改 / 删除，
 * 系统视角只读不可改。
 *
 * <p><b>接口分组：</b>
 * <ul>
 *   <li><b>发表</b>：{@code POST /workflow/comment} — 发表评论 / 回复（{@code parentCommentId} 非空时为回复）</li>
 *   <li><b>查询</b>：
 *     <ul>
 *       <li>{@code GET /workflow/comment/instance/{instanceId}} — 实例全部评论（树结构）</li>
 *       <li>{@code GET /workflow/comment/root/{instanceId}} — 实例一级评论</li>
 *       <li>{@code GET /workflow/comment/replies/{parentCommentId}} — 父评论下的回复</li>
 *     </ul>
 *   </li>
 *   <li><b>删除</b>：{@code DELETE /workflow/comment/{commentId}} — 软删除（仅本人）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>所有写接口通过 {@link AuthApiPermission} 校验
 * {@link PermissionCodes#WORKFLOW_COMMENT_OPERATE} 权限码；删除操作额外校验
 * 「操作用户 == 评论人」防越权。
 *
 * <p><b>限流：</b>发表 / 回复通过 {@link Idempotent} 5s 防重；删除通过 {@link Idempotent} 防重。
 *
 * <p><b>性能优化：</b>实例全量评论一次性拉取后由前端本地组装树，避免 N+1 查询；
 * 评论分页采用 {@code (created_at, parent_comment_id)} 复合索引。
 *
 * <p><b>通知触发：</b>评论 / 回复时由 {@link FlowCommentService} 调用
 * {@code FlowNotificationService} 通知被回复人；提及人（{@code @xxx}）索引便于 @ 检索。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see FlowCommentService 评论服务
 * @see FlowComment 评论实体
 * @see FlowCommentCreateDTO 评论创建 DTO
 */
@Slf4j
@RestController
@Tag(name = "workflow-comment", description = "工作流审批评论接口")
@RequestMapping("/api/v1/workflow/comment")
@RequiredArgsConstructor
public class FlowCommentController {

    /** 流程评论服务，负责评论/回复的发表、查询与删除 */
    private final FlowCommentService commentService;

    /**
     * 发表评论或回复
     *
     * @param dto 评论参数
     * @return 统一响应结果，包含新评论 ID
     */
    @Idempotent(key = "remi:workflow:FlowCommentController:addComment:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowcomment.addComment", threshold = 50)
    @PostMapping
    @Audit(module = "流程评论", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'addComment'")
    @Operation(summary = "发表评论/回复")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<String> addComment(@Valid @RequestBody FlowCommentCreateDTO dto) {
        String userId = AuthContextUtils.getUserId();
        String userName = AuthContextUtils.getUsername();
        String tenantId = AuthContextUtils.getTenantIdOrDefault("1");
        return BaseResponse.success(commentService.addComment(dto, userId, userName, tenantId));
    }

    /**
     * 查询实例下全部评论（一级 + 回复，按创建时间正序）
     *
     * @param instanceId 实例 ID
     * @return 统一响应结果，包含全部评论列表
     */
    @GetMapping("/instance/{instanceId}")
    @Operation(summary = "查询实例全部评论（树结构）")
    public BaseResponse<List<FlowCommentVO>> listByInstance(@PathVariable String instanceId) {
        String tenantId = AuthContextUtils.getTenantIdOrDefault("1");
        return BaseResponse.success(WorkflowConverter.INSTANT.flowCommentListToVO(commentService.listByInstance(tenantId, instanceId)));
    }

    /**
     * 查询实例下全部一级评论（不含回复）
     *
     * @param instanceId 实例 ID
     * @return 统一响应结果，包含一级评论列表
     */
    @GetMapping("/root/{instanceId}")
    @Operation(summary = "查询实例一级评论")
    public BaseResponse<List<FlowCommentVO>> listRootComments(@PathVariable String instanceId) {
        String tenantId = AuthContextUtils.getTenantIdOrDefault("1");
        return BaseResponse.success(WorkflowConverter.INSTANT.flowCommentListToVO(commentService.listRootComments(tenantId, instanceId)));
    }

    /**
     * 查询指定父评论下的全部回复
     *
     * @param parentCommentId 父评论 ID
     * @return 统一响应结果，包含回复列表
     */
    @GetMapping("/replies/{parentCommentId}")
    @Operation(summary = "查询父评论下的回复")
    public BaseResponse<List<FlowCommentVO>> listReplies(@PathVariable String parentCommentId) {
        return BaseResponse.success(WorkflowConverter.INSTANT.flowCommentListToVO(commentService.listReplies(parentCommentId)));
    }

    /**
     * 删除评论（仅评论人本人可删除）
     *
     * @param commentId 评论 ID
     * @return 统一响应结果，包含是否删除成功
     */
    @Idempotent(key = "remi:workflow:FlowCommentController:deleteComment:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowcomment.deleteComment", threshold = 50)
    @DeleteMapping("/{commentId}")
    @Audit(module = "流程评论", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'deleteComment'")
    @Operation(summary = "删除评论（仅本人）")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Boolean> deleteComment(@PathVariable String commentId) {
        String userId = AuthContextUtils.getUserId();
        return BaseResponse.success(commentService.deleteComment(commentId, userId));
    }
}
