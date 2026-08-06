package com.remisoft.workflow.web.controller.notification;

import java.util.List;

import com.remisoft.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.remisoft.common.auth.context.AuthContextUtils;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.common.security.TenantContext;
import com.remisoft.workflow.domain.dto.FlowQuickCommentDTO;
import com.remisoft.workflow.domain.entity.FlowQuickComment;
import com.remisoft.workflow.server.service.FlowQuickCommentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.remisoft.workflow.domain.converter.WorkflowConverter;
import com.remisoft.workflow.domain.vo.FlowQuickCommentVO;

import com.remisoft.common.audit.annotation.Audit;
import com.remisoft.common.audit.enums.AuditAction;
import com.remisoft.common.audit.enums.AuditType;
/**
 * 审批常用语 Controller
 *
 * <p>对标钉钉 / 飞书审批的「常用语」能力，提供审批人常用审批意见的 HTTP 接口。
 * 系统预设 + 用户自定义双轨制，使用次数智能排序。
 *
 * <p><b>接口分组：</b>
 * <ul>
 *   <li><b>查询</b>：{@code GET /quickComments}（当前用户常用语，按 sortNum + useCount 排序）</li>
 *   <li><b>新增</b>：{@code POST /quickComments}（新增用户自定义常用语）</li>
 *   <li><b>编辑</b>：{@code PUT /quickComments/{id}}（仅编辑本人创建的）</li>
 *   <li><b>删除</b>：{@code DELETE /quickComments/{id}}（系统预设不可删）</li>
 *   <li><b>计数</b>：{@code POST /quickComments/{id}/use}（审批时调用 +1）</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有操作按 {@link TenantContext} 当前租户隔离；
 * 跨租户常用语不可见。
 *
 * <p><b>权限模型：</b>所有写接口通过 {@link Idempotent} 5s 防重；
 * 删除操作额外校验「操作用户 == 创建人」防越权。
 *
 * <p><b>性能优化：</b>查询按 {@code (user_id, tenant_id, is_system, sort_num)} 复合索引；
 * 使用次数走 {@code use_count} 字段，前端按 sortNum + useCount 排序展示。
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传与租户注入；常用语业务逻辑下沉到
 * {@link FlowQuickCommentService}，使用次数自增由 Service 通过单条 UPDATE SQL 完成。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see FlowQuickCommentService 常用语服务
 * @see FlowQuickComment 常用语实体
 * @see FlowQuickCommentDTO 常用语 DTO
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/workflow/quickComments")
@RequiredArgsConstructor
@Tag(name = "审批常用语", description = "常用审批意见管理")
public class FlowQuickCommentController {

    /** 审批常用语服务，负责常用语的增删改查与使用次数统计 */
    private final FlowQuickCommentService quickCommentService;

    /**
     * 查询当前用户的常用语列表。
     *
     * @return 常用语列表
     */
    @GetMapping
    @Operation(summary = "查询当前用户的常用语列表")
    public BaseResponse<List<FlowQuickCommentVO>> list() {
        String userId = AuthContextUtils.getUserId();
        String tenantId = TenantContext.getTenantId();
        return BaseResponse.success(WorkflowConverter.INSTANT.flowQuickCommentListToVO(quickCommentService.listByUser(userId, tenantId)));
    }

    /**
     * 新增常用语。
     *
     * @param dto 常用语信息
     * @return 新建常用语 ID
     */
    @Idempotent(key = "remi:workflow:FlowQuickCommentController:create:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowquickcomment.create", threshold = 50)
    @PostMapping
    @Audit(module = "流程评论", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'create'")
    @Operation(summary = "新增常用语")
    public BaseResponse<String> create(@Valid @RequestBody FlowQuickCommentDTO dto) {
        String userId = AuthContextUtils.getUserId();
        String tenantId = TenantContext.getTenantId();
        return BaseResponse.success(quickCommentService.create(dto, userId, tenantId));
    }

    /**
     * 编辑常用语。
     *
     * @param dto 常用语信息
     * @return 空响应
     */
    @Idempotent(key = "remi:workflow:FlowQuickCommentController:update:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowquickcomment.update", threshold = 50)
    @PutMapping
    @Audit(module = "流程评论", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'update'")
    @Operation(summary = "编辑常用语")
    public BaseResponse<Void> update(@Valid @RequestBody FlowQuickCommentDTO dto) {
        String userId = AuthContextUtils.getUserId();
        quickCommentService.update(dto, userId);
        return BaseResponse.success();
    }

    /**
     * 删除常用语。
     *
     * @param id 常用语 ID
     * @return 空响应
     */
    @Idempotent(key = "remi:workflow:FlowQuickCommentController:delete:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowquickcomment.delete", threshold = 50)
    @DeleteMapping("/{id}")
    @Audit(module = "流程评论", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'delete'")
    @Operation(summary = "删除常用语")
    public BaseResponse<Void> delete(@PathVariable String id) {
        String userId = AuthContextUtils.getUserId();
        quickCommentService.delete(id, userId);
        return BaseResponse.success();
    }

    /**
     * 增加使用次数（审批时调用）。
     *
     * @param id 常用语 ID
     * @return 空响应
     */
    @Idempotent(key = "remi:workflow:FlowQuickCommentController:incrementUseCount:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowquickcomment.incrementUseCount", threshold = 50)
    @PostMapping("/{id}/use")
    @Audit(module = "流程评论", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'incrementUseCount'")
    @Operation(summary = "增加使用次数（审批时调用）")
    public BaseResponse<Void> incrementUseCount(@PathVariable String id) {
        quickCommentService.incrementUseCount(id);
        return BaseResponse.success();
    }
}
