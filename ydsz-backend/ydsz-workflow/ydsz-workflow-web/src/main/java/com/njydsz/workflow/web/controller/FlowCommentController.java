package com.njydsz.workflow.web.controller.notification;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.workflow.domain.dto.FlowCommentCreateDTO;
import com.njydsz.workflow.domain.entity.FlowComment;
import com.njydsz.workflow.server.service.FlowCommentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.vo.FlowCommentVO;

/**
 * P2-2: 流程评论 Controller
 *
 * <p>审批评论多级回复接口。对标钉钉/飞书审批评论区。
 *
 * <p>端点：
 * <ul>
 *   <li>POST /workflow/comment — 发表评论/回复</li>
 *   <li>GET /workflow/comment/instance/{instanceId} — 查询实例全部评论（树结构）</li>
 *   <li>GET /workflow/comment/root/{instanceId} — 查询实例一级评论</li>
 *   <li>GET /workflow/comment/replies/{parentCommentId} — 查询父评论下的回复</li>
 *   <li>DELETE /workflow/comment/{commentId} — 删除评论（仅本人）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
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
    @Idempotent(key = "ydsz:workflow:FlowCommentController:addComment:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowcomment.addComment", threshold = 50)
    @PostMapping
    @Operation(summary = "发表评论/回复")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<String> addComment(@Valid @RequestBody FlowCommentCreateDTO dto) {
        String userId = AuthContext.getUserId();
        String userName = AuthContext.getUsername();
        String tenantId = AuthContext.getTenantIdOrDefault("1");
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
        String tenantId = AuthContext.getTenantIdOrDefault("1");
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
        String tenantId = AuthContext.getTenantIdOrDefault("1");
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
    @Idempotent(key = "ydsz:workflow:FlowCommentController:deleteComment:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowcomment.deleteComment", threshold = 50)
    @DeleteMapping("/{commentId}")
    @Operation(summary = "删除评论（仅本人）")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
    public BaseResponse<Boolean> deleteComment(@PathVariable String commentId) {
        String userId = AuthContext.getUserId();
        return BaseResponse.success(commentService.deleteComment(commentId, userId));
    }
}
