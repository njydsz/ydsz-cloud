package com.njydsz.workflow.web.controller.ai;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.workflow.server.service.FlowCanaryService;
/**
 * 灰度发布 Controller
 *
 * <p>P3-1: 流程灰度发布接口（P1-10 从 FlowEngineController 拆分）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/engine/canary/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>启动灰度</b>：{@code POST /canary/{id}/publish} — 标记为灰度版本，按比例切流</li>
 *   <li><b>比例调整</b>：{@code POST /canary/{id}/adjust} — 灰度放量/缩量</li>
 *   <li><b>全量发布</b>：{@code POST /canary/{id}/promote} — 灰度版晋升为稳定版</li>
 *   <li><b>灰度回滚</b>：{@code POST /canary/{id}/rollback} — 灰度失败时回滚到稳定版</li>
 *   <li><b>历史查询</b>：{@code GET /canary/{flowCode}/rolloutLog} — 灰度发布历史</li>
 * </ul>
 *
 * <p><b>切流策略：</b>
 * <ul>
 *   <li><b>USER_HASH</b>：按用户 ID 哈希取模，保证同一用户始终落到同一版本（推荐）</li>
 *   <li><b>RANDOM</b>：纯随机分发，简单但同一用户可能命中不同版本</li>
 *   <li><b>WHITELIST</b>：白名单用户优先灰度版本，其余用户稳定版本</li>
 * </ul>
 *
 * <p><b>权限要求：</b>{@link PermissionCodes#WORKFLOW_CANARY_MANAGE} 灰度发布管理权限码。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>所有接口启用 {@link Idempotent} 防重（5s）</li>
 *   <li>操作人 ID/姓名从 {@link AuthContextUtils} SecurityContext 获取，不暴露为 URL 参数</li>
 *   <li>灰度回滚会自动生成审计日志，便于追溯</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.FlowCanaryService 灰度发布 Service
 * @see com.njydsz.workflow.domain.enums.CanaryStrategy 切流策略枚举
 */
@Slf4j
@RestController
@Tag(name = "workflow-canary", description = "工作流灰度发布接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowCanaryController {

    /** P3-1: 灰度发布服务 */
    private final FlowCanaryService canaryService;

    /**
     * 启动灰度发布
     *
     * <p>将指定流程定义标记为灰度版，按 {@code initialPercent} 切流。
     * <p>启动后立即生效，新增流程实例按策略路由到灰度版或稳定版。
     * <p>操作人 ID/姓名从 SecurityContext 获取。
     *
     * @param definitionId   灰度版定义 ID
     * @param initialPercent 初始灰度比例（0-100）
     * @param strategy       切流策略：USER_HASH / RANDOM / WHITELIST
     * @param note           备注
     * @return 空响应
     */
    @Idempotent(key = "ydsz:workflow:FlowCanaryController:publishCanary:lock", ttlSeconds = 5)
    @PostMapping("/canary/{definitionId}/publish")
    @Audit(module = "灰度发布", type = AuditType.OPERATION, action = AuditAction.ENABLE, content = "'publishCanary'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_CANARY_MANAGE)
    public BaseResponse<Void> publishCanary(
            @PathVariable String definitionId,
            @RequestParam(defaultValue = "10") int initialPercent,
            @RequestParam(defaultValue = "USER_HASH") String strategy,
            @RequestParam(required = false) String note) {
        canaryService.publishCanary(definitionId, initialPercent, strategy,
                AuthContextUtils.getUserId(), AuthContextUtils.getUsername(), note);
        return BaseResponse.success();
    }

    /**
     * 调整灰度比例（逐步放量/缩量）
     *
     * <p>支持渐进式发布：5% → 20% → 50% → 100%，逐步放量降低风险。
     * <p>操作人 ID/姓名从 SecurityContext 获取。
     *
     * @param definitionId 定义 ID
     * @param newPercent   新比例（0-100）
     * @param note         备注
     * @return 空响应
     */
    @Idempotent(key = "ydsz:workflow:FlowCanaryController:adjustCanary:lock", ttlSeconds = 5)
    @PostMapping("/canary/{definitionId}/adjust")
    @Audit(module = "灰度发布", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'adjustCanary'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_CANARY_MANAGE)
    public BaseResponse<Void> adjustCanary(
            @PathVariable String definitionId,
            @RequestParam int newPercent,
            @RequestParam(required = false) String note) {
        canaryService.adjustCanaryPercent(definitionId, newPercent,
                AuthContextUtils.getUserId(), AuthContextUtils.getUsername(), note);
        return BaseResponse.success();
    }

    /**
     * 全量发布 — 灰度版晋升为稳定版
     *
     * <p>将灰度版定义的状态置为 PUBLISHED（稳定版），所有新实例全部路由到该版本。
     * <p>建议在灰度验证通过（无异常 / 监控指标平稳）后调用。
     * <p>操作人 ID/姓名从 SecurityContext 获取。
     *
     * @param definitionId 灰度版定义 ID
     * @param note         备注
     * @return 空响应
     */
    @Idempotent(key = "ydsz:workflow:FlowCanaryController:promoteCanary:lock", ttlSeconds = 5)
    @PostMapping("/canary/{definitionId}/promote")
    @Audit(module = "灰度发布", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'promoteCanary'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_CANARY_MANAGE)
    public BaseResponse<Void> promoteCanary(
            @PathVariable String definitionId,
            @RequestParam(required = false) String note) {
        canaryService.promoteCanary(definitionId,
                AuthContextUtils.getUserId(), AuthContextUtils.getUsername(), note);
        return BaseResponse.success();
    }

    /**
     * 灰度回滚
     *
     * <p>灰度发布出现异常时立即回滚：将灰度版标记为 ROLLBACK，所有新实例回到稳定版。
     * <p>在途实例不受影响（仍按创建时路由的版本推进），需配合流程定义回滚接口处理。
     * <p>操作人 ID/姓名从 SecurityContext 获取。
     *
     * @param definitionId 灰度版定义 ID
     * @param note         备注（含回滚原因）
     * @return 空响应
     */
    @Idempotent(key = "ydsz:workflow:FlowCanaryController:rollbackCanary:lock", ttlSeconds = 5)
    @PostMapping("/canary/{definitionId}/rollback")
    @Audit(module = "灰度发布", type = AuditType.OPERATION, action = AuditAction.RESTORE, content = "'rollbackCanary'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_CANARY_MANAGE)
    public BaseResponse<Void> rollbackCanary(
            @PathVariable String definitionId,
            @RequestParam(required = false) String note) {
        canaryService.rollbackCanary(definitionId,
                AuthContextUtils.getUserId(), AuthContextUtils.getUsername(), note);
        return BaseResponse.success();
    }

    /**
     * 查询某 flowCode 的灰度发布历史
     *
     * <p>返回该流程编码下的全部灰度 rollout 日志，含发布时间、操作人、灰度比例变化、状态变更。
     * <p>按时间倒序排列。
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID（可选）
     * @return rollout 日志列表
     */
    @GetMapping("/canary/{flowCode}/rolloutLog")
    public BaseResponse<List<Map<String, Object>>> rolloutLog(
            @PathVariable String flowCode,
            @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
        return BaseResponse.success(canaryService.listCanaryRolloutLog(flowCode, tid));
    }
}
