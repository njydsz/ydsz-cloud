package com.njydsz.pmis.workflow.web.controller.notification;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.workflow.domain.dto.FlowQuickCommentDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowQuickCommentDO;
import com.njydsz.pmis.workflow.server.service.FlowQuickCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审批常用语 Controller
 *
 * <p>P1-2: 对标钉钉/飞书审批的"常用语"能力。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/workflow/quickComments")
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
    public BaseResponse<List<FlowQuickCommentDO>> list() {
        String userId = AuthContext.getUserId();
        String tenantId = TenantContext.getTenantId();
        return BaseResponse.ok(quickCommentService.listByUser(userId, tenantId));
    }

    /**
     * 新增常用语。
     *
     * @param dto 常用语信息
     * @return 新建常用语 ID
     */
    @Idempotent(key = "flowQuickComment:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    @Operation(summary = "新增常用语")
    public BaseResponse<String> create(@Valid @RequestBody FlowQuickCommentDTO dto) {
        String userId = AuthContext.getUserId();
        String tenantId = TenantContext.getTenantId();
        return BaseResponse.ok(quickCommentService.create(dto, userId, tenantId));
    }

    /**
     * 编辑常用语。
     *
     * @param dto 常用语信息
     * @return 空响应
     */
    @Idempotent(key = "flowQuickComment:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping
    @Operation(summary = "编辑常用语")
    public BaseResponse<Void> update(@Valid @RequestBody FlowQuickCommentDTO dto) {
        String userId = AuthContext.getUserId();
        quickCommentService.update(dto, userId);
        return BaseResponse.ok();
    }

    /**
     * 删除常用语。
     *
     * @param id 常用语 ID
     * @return 空响应
     */
    @Idempotent(key = "flowQuickComment:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除常用语")
    public BaseResponse<Void> delete(@PathVariable String id) {
        String userId = AuthContext.getUserId();
        quickCommentService.delete(id, userId);
        return BaseResponse.ok();
    }

    /**
     * 增加使用次数（审批时调用）。
     *
     * @param id 常用语 ID
     * @return 空响应
     */
    @Idempotent(key = "flowQuickComment:incrementUseCount", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/use")
    @Operation(summary = "增加使用次数（审批时调用）")
    public BaseResponse<Void> incrementUseCount(@PathVariable String id) {
        quickCommentService.incrementUseCount(id);
        return BaseResponse.ok();
    }
}
