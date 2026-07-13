package com.njydsz.pmis.workflow.web.controller.delegate;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.core.response.PageResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.workflow.domain.dto.FlowDelegateAuthSaveDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowDelegateAuthDO;
import com.njydsz.pmis.workflow.server.service.FlowDelegateAuthService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
@RequestMapping("/workflow/engine")
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
    @Idempotent(key = "flowDelegate:createDelegateAuth", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/delegateAuth/create")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
    public BaseResponse<String> createDelegateAuth(@Valid @RequestBody FlowDelegateAuthSaveDTO dto) {
        FlowDelegateAuthDO auth = new FlowDelegateAuthDO();
        BeanUtils.copyProperties(dto, auth);
        // 从 SecurityContext 兜底 ownerUserId（防止前端漏传）
        if (auth.getOwnerUserId() == null) {
            auth.setOwnerUserId(AuthContext.getUserId());
        }
        String id = delegateAuthService.create(auth);
        return BaseResponse.ok(id);
    }

    /**
     * P1-4: 撤回授权。
     *
     * @param id 授权记录 ID
     * @return 空响应
     */
    @Idempotent(key = "flowDelegate:revokeDelegateAuth", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/delegateAuth/{id}/revoke")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
    public BaseResponse<Void> revokeDelegateAuth(@PathVariable String id) {
        String ownerId = AuthContext.getUserId();
        delegateAuthService.revoke(id, ownerId);
        return BaseResponse.ok();
    }

    /**
     * P1-4: 启用/停用授权。
     *
     * @param id     授权记录 ID
     * @param status 目标状态
     * @return 空响应
     */
    @Idempotent(key = "flowDelegate:updateDelegateAuthStatus", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/delegateAuth/{id}/status")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
    public BaseResponse<Void> updateDelegateAuthStatus(@PathVariable String id,
                                                 @RequestParam String status) {
        String operatorId = AuthContext.getUserId();
        delegateAuthService.updateStatus(id, status, operatorId);
        return BaseResponse.ok();
    }

    /**
     * P1-4: 查"我设置的"授权列表。
     *
     * @param status 状态筛选（可选）
     * @return 授权列表
     */
    @GetMapping("/delegateAuth/mine")
    public BaseResponse<List<FlowDelegateAuthDO>> listMyDelegateAuths(
            @RequestParam(required = false) String status) {
        String ownerId = AuthContext.getUserId();
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.ok(delegateAuthService.listMine(ownerId, tenantId, status));
    }

    /**
     * P1-4: 查"代理给我的"授权列表。
     *
     * @param status 状态筛选（可选）
     * @return 授权列表
     */
    @GetMapping("/delegateAuth/asDelegate")
    public BaseResponse<List<FlowDelegateAuthDO>> listAsDelegate(
            @RequestParam(required = false) String status) {
        String delegateUserId = AuthContext.getUserId();
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.ok(delegateAuthService.listAsDelegate(delegateUserId, tenantId, status));
    }

    /**
     * P1-4: 查"我代理处理了哪些任务"。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 委派处理日志分页
     */
    @GetMapping("/delegateAuth/log/delegate")
    public BaseResponse<PageResponse<?>> myDelegateLog(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        String delegateUserId = AuthContext.getUserId();
        return BaseResponse.ok(delegateAuthService.listDelegateLog(delegateUserId, page, size));
    }

    /**
     * P1-4: 查"我的哪些任务被代理了"。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 被代理任务日志分页
     */
    @GetMapping("/delegateAuth/log/owner")
    public BaseResponse<PageResponse<?>> myOwnerLog(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        String ownerUserId = AuthContext.getUserId();
        return BaseResponse.ok(delegateAuthService.listOwnerLog(ownerUserId, page, size));
    }
}
