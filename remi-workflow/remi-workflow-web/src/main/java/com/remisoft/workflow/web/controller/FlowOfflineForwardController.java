package com.remisoft.workflow.web.controller.delegate;

import org.springframework.web.bind.annotation.*;

import com.remisoft.common.safe.ratelimit.annotation.RateLimit;
import com.remisoft.common.auth.context.AuthContext;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.workflow.server.service.FlowOfflineAutoForwardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.remisoft.common.audit.annotation.Audit;
import com.remisoft.common.audit.enums.AuditAction;
import com.remisoft.common.audit.enums.AuditType;
/**
 * 离线代理自动转发 Controller（P2-5）
 *
 * <p>对标钉钉/飞书审批「离职/休假自动转交」能力。提供<b>离线用户</b>已有待办的
 * <b>自动 / 手动</b>转发入口：当用户离线（连续 N 天未登录 / HR 标记离职 / 长期请假）时，
 * 将其名下 PENDING/CLAIMED 状态的待办按<b>代理授权规则</b>或<b>指定代理人</b>转交，
 * 避免审批流因个人原因挂起。
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/offlineForward/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>自动转发</b>：{@code POST /auto} — 按 {@code authId}（代理授权记录）扫描该用户已有待办，
 *       按授权的 {@code delegateUserId} 批量转交</li>
 *   <li><b>手动转发</b>：{@code POST /manual} — 管理员手动指定转交源用户与目标代理人，立即转交</li>
 * </ul>
 *
 * <p><b>转交流程：</b>
 * <ol>
 *   <li>校验源用户当前是否有有效代理授权（手动转发跳过此步）</li>
 *   <li>查询源用户 PENDING/CLAIMED 状态的待办任务</li>
 *   <li>事务内批量更新任务的 {@code assigneeId} 为代理人 ID</li>
 *   <li>写入 {@code remi_flow_delegate_log} 委派日志（含原 assigneeId / 新 assigneeId / 原因 / 时间）</li>
 *   <li>发送通知给代理人（新待办消息 / 站内信 / 邮件）</li>
 * </ol>
 *
 * <p><b>权限要求：</b>写接口仅限管理员（{@code workflow:offlineForward:operate} 权限码）。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>所有接口启用 {@link Idempotent} 5s 防重</li>
 *   <li>所有接口启用 {@link RateLimit} 50 QPS 限流</li>
 *   <li>手动转发记录操作人 ID（从 SecurityContext 获取），便于审计追溯</li>
 * </ul>
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传与权限校验；
 * 任务扫描、批量转交、通知触发下沉到 {@link FlowOfflineAutoForwardService}。
 *
 * @author remi-team
 * @since 1.0.0
 * @see FlowOfflineAutoForwardService 离线自动转发服务
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflow/offlineForward")
@RequiredArgsConstructor
@Tag(name = "离线代理自动转发", description = "离线用户的待办自动转发给代理人")
public class FlowOfflineForwardController {

    /** 离线代理自动转发服务，负责离线用户待办的自动/手动转发 */
    private final FlowOfflineAutoForwardService offlineAutoForwardService;

    /**
     * 按代理授权规则自动转发已有待办
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p>业务流程：
     * <ol>
     *   <li>读取 {@code authId} 对应的代理授权（含 {@code ownerUserId}、{@code delegateUserId}、生效时间）</li>
     *   <li>扫描 {@code ownerUserId} 全部 PENDING/CLAIMED 任务</li>
     *   <li>事务内批量改派 + 写委派日志</li>
     *   <li>触发通知给代理人</li>
     * </ol>
     *
     * @param authId 代理授权记录 ID
     * @return 成功转发的任务数
     */
    @Idempotent(key = "remi:workflow:FlowOfflineForwardController:autoForward:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowofflineforward.autoForward", threshold = 50)
    @PostMapping("/auto")
    @Audit(module = "离线转发", type = AuditType.OPERATION, action = AuditAction.GRANT, content = "'autoForward'")
    @Operation(summary = "按代理授权规则自动转发已有待办")
    public BaseResponse<Integer> autoForward(@RequestParam String authId) {
        return BaseResponse.success(offlineAutoForwardService.autoForwardByAuth(authId));
    }

    /**
     * 手动触发离线转发
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p>适用于：HR 标记员工离职 / 管理员临时调岗等需要<b>立即</b>把某人待办转给指定代理人的场景。
     * <p>操作人 ID 从 SecurityContext 获取，写入委派日志用于审计。
     *
     * @param userId        离线用户 ID（源用户）
     * @param delegateUserId 代理人 ID（目标用户）
     * @return 成功转发的任务数
     */
    @Idempotent(key = "remi:workflow:FlowOfflineForwardController:manualForward:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowofflineforward.manualForward", threshold = 50)
    @PostMapping("/manual")
    @Audit(module = "离线转发", type = AuditType.OPERATION, action = AuditAction.GRANT, content = "'manualForward'")
    @Operation(summary = "手动触发离线转发")
    public BaseResponse<Integer> manualForward(
            @RequestParam String userId,
            @RequestParam String delegateUserId) {
        String operatorId = AuthContext.getUserId();
        return BaseResponse.success(offlineAutoForwardService.manualForward(userId, delegateUserId, operatorId));
    }
}
