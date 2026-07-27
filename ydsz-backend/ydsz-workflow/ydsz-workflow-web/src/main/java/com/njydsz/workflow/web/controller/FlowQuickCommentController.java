package com.njydsz.workflow.web.controller.notification;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.security.TenantContext;
import com.njydsz.workflow.domain.dto.FlowQuickCommentDTO;
import com.njydsz.workflow.domain.entity.FlowQuickComment;
import com.njydsz.workflow.server.service.FlowQuickCommentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.vo.FlowQuickCommentVO;

/**
 * 审批常用语 Controller
 *
 * <p>P1-2: 对标钉钉/飞书审批的"常用语"能力。
 *
 * @author ydsz-team
 * @since 1.0.0
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
        String userId = AuthContext.getUserId();
        String tenantId = TenantContext.getTenantId();
        return BaseResponse.success(WorkflowConverter.INSTANT.flowQuickCommentListToVO(quickCommentService.listByUser(userId, tenantId)));
    }

    /**
     * 新增常用语。
     *
     * @param dto 常用语信息
     * @return 新建常用语 ID
     */
    @Idempotent(key = "ydsz:workflow:FlowQuickCommentController:create:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowquickcomment.create", threshold = 50)
    @PostMapping
    @Operation(summary = "新增常用语")
    public BaseResponse<String> create(@Valid @RequestBody FlowQuickCommentDTO dto) {
        String userId = AuthContext.getUserId();
        String tenantId = TenantContext.getTenantId();
        return BaseResponse.success(quickCommentService.create(dto, userId, tenantId));
    }

    /**
     * 编辑常用语。
     *
     * @param dto 常用语信息
     * @return 空响应
     */
    @Idempotent(key = "ydsz:workflow:FlowQuickCommentController:update:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowquickcomment.update", threshold = 50)
    @PutMapping
    @Operation(summary = "编辑常用语")
    public BaseResponse<Void> update(@Valid @RequestBody FlowQuickCommentDTO dto) {
        String userId = AuthContext.getUserId();
        quickCommentService.update(dto, userId);
        return BaseResponse.success();
    }

    /**
     * 删除常用语。
     *
     * @param id 常用语 ID
     * @return 空响应
     */
    @Idempotent(key = "ydsz:workflow:FlowQuickCommentController:delete:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowquickcomment.delete", threshold = 50)
    @DeleteMapping("/{id}")
    @Operation(summary = "删除常用语")
    public BaseResponse<Void> delete(@PathVariable String id) {
        String userId = AuthContext.getUserId();
        quickCommentService.delete(id, userId);
        return BaseResponse.success();
    }

    /**
     * 增加使用次数（审批时调用）。
     *
     * @param id 常用语 ID
     * @return 空响应
     */
    @Idempotent(key = "ydsz:workflow:FlowQuickCommentController:incrementUseCount:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowquickcomment.incrementUseCount", threshold = 50)
    @PostMapping("/{id}/use")
    @Operation(summary = "增加使用次数（审批时调用）")
    public BaseResponse<Void> incrementUseCount(@PathVariable String id) {
        quickCommentService.incrementUseCount(id);
        return BaseResponse.success();
    }
}
