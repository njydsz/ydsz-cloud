package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.dto.FlowDelegateAuthSaveDTO;
import com.njydsz.pmis.workflow.entity.FlowDelegateAuthDO;
import com.njydsz.pmis.workflow.service.FlowDelegateAuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 长期授权委派 Controller
 *
 * <p>P1-4: 长期授权委派接口（P1-10 从 FlowEngineController 拆分）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-delegate", description = "工作流授权委派接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowDelegateController {

    /** P1-4: 长期授权委派服务 */
    private final FlowDelegateAuthService delegateAuthService;

    /**
     * P1-4: 创建长期授权委派
     *
     * <p>业务示例：用户 A 休假 7 天，希望 B 代理处理所有流程。
     * 提交时 body 形如：
     * <pre>
     * {
     *   "ownerUserId": 1001,
     *   "ownerUserName": "张三",
     *   "delegateUserId": 1002,
     *   "delegateUserName": "李四",
     *   "scopeType": "ALL",
     *   "startTime": "2026-07-02T00:00:00",
     *   "endTime": "2026-07-09T23:59:59",
     *   "reason": "年假"
     * }
     * </pre>
     */
    @PostMapping("/delegate-auth/create")
    @PrePermission(PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
    public Result<Long> createDelegateAuth(@Valid @RequestBody FlowDelegateAuthSaveDTO dto) {
        FlowDelegateAuthDO auth = new FlowDelegateAuthDO();
        BeanUtils.copyProperties(dto, auth);
        // 从 SecurityContext 兜底 ownerUserId（防止前端漏传）
        if (auth.getOwnerUserId() == null) {
            auth.setOwnerUserId(SecurityContext.getUserId());
        }
        Long id = delegateAuthService.create(auth);
        return Result.ok(id);
    }

    /**
     * P1-4: 撤回授权
     */
    @PostMapping("/delegate-auth/{id}/revoke")
    @PrePermission(PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
    public Result<Void> revokeDelegateAuth(@PathVariable @Min(1) Long id) {
        Long ownerId = SecurityContext.getUserId();
        delegateAuthService.revoke(id, ownerId);
        return Result.ok();
    }

    /**
     * P1-4: 启用/停用授权
     */
    @PostMapping("/delegate-auth/{id}/status")
    @PrePermission(PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
    public Result<Void> updateDelegateAuthStatus(@PathVariable @Min(1) Long id,
                                                 @RequestParam String status) {
        Long operatorId = SecurityContext.getUserId();
        delegateAuthService.updateStatus(id, status, operatorId);
        return Result.ok();
    }

    /**
     * P1-4: 查"我设置的"授权列表
     */
    @GetMapping("/delegate-auth/mine")
    public Result<List<FlowDelegateAuthDO>> listMyDelegateAuths(
            @RequestParam(required = false) String status) {
        Long ownerId = SecurityContext.getUserId();
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(delegateAuthService.listMine(ownerId, tenantId, status));
    }

    /**
     * P1-4: 查"代理给我的"授权列表
     */
    @GetMapping("/delegate-auth/as-delegate")
    public Result<List<FlowDelegateAuthDO>> listAsDelegate(
            @RequestParam(required = false) String status) {
        Long delegateUserId = SecurityContext.getUserId();
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(delegateAuthService.listAsDelegate(delegateUserId, tenantId, status));
    }

    /**
     * P1-4: 查"我代理处理了哪些任务"
     */
    @GetMapping("/delegate-auth/log/delegate")
    public Result<PageResult<?>> myDelegateLog(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Long delegateUserId = SecurityContext.getUserId();
        return Result.ok(delegateAuthService.listDelegateLog(delegateUserId, page, size));
    }

    /**
     * P1-4: 查"我的哪些任务被代理了"
     */
    @GetMapping("/delegate-auth/log/owner")
    public Result<PageResult<?>> myOwnerLog(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Long ownerUserId = SecurityContext.getUserId();
        return Result.ok(delegateAuthService.listOwnerLog(ownerUserId, page, size));
    }
}
