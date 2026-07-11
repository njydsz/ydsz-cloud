package com.njydsz.pmis.workflow.web.controller.notification;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.domain.dto.notification.FlowCommentCreateDTO;
import com.njydsz.pmis.workflow.domain.entity.notification.FlowCommentDO;
import com.njydsz.pmis.workflow.server.service.notification.FlowCommentService;
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

import java.util.List;

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
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-comment", description = "工作流审批评论接口")
@RequestMapping("/workflow/comment")
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
    @Idempotent(key = "flowComment:addComment", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    @Operation(summary = "发表评论/回复")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<String> addComment(@Valid @RequestBody FlowCommentCreateDTO dto) {
        String userId = SecurityContext.getUserId();
        String userName = SecurityContext.getUsername();
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(commentService.addComment(dto, userId, userName, tenantId));
    }

    /**
     * 查询实例下全部评论（一级 + 回复，按创建时间正序）
     *
     * @param instanceId 实例 ID
     * @return 统一响应结果，包含全部评论列表
     */
    @GetMapping("/instance/{instanceId}")
    @Operation(summary = "查询实例全部评论（树结构）")
    public Result<List<FlowCommentDO>> listByInstance(@PathVariable String instanceId) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(commentService.listByInstance(tenantId, instanceId));
    }

    /**
     * 查询实例下全部一级评论（不含回复）
     *
     * @param instanceId 实例 ID
     * @return 统一响应结果，包含一级评论列表
     */
    @GetMapping("/root/{instanceId}")
    @Operation(summary = "查询实例一级评论")
    public Result<List<FlowCommentDO>> listRootComments(@PathVariable String instanceId) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(commentService.listRootComments(tenantId, instanceId));
    }

    /**
     * 查询指定父评论下的全部回复
     *
     * @param parentCommentId 父评论 ID
     * @return 统一响应结果，包含回复列表
     */
    @GetMapping("/replies/{parentCommentId}")
    @Operation(summary = "查询父评论下的回复")
    public Result<List<FlowCommentDO>> listReplies(@PathVariable String parentCommentId) {
        return Result.ok(commentService.listReplies(parentCommentId));
    }

    /**
     * 删除评论（仅评论人本人可删除）
     *
     * @param commentId 评论 ID
     * @return 统一响应结果，包含是否删除成功
     */
    @Idempotent(key = "flowComment:deleteComment", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{commentId}")
    @Operation(summary = "删除评论（仅本人）")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Boolean> deleteComment(@PathVariable String commentId) {
        String userId = SecurityContext.getUserId();
        return Result.ok(commentService.deleteComment(commentId, userId));
    }
}
