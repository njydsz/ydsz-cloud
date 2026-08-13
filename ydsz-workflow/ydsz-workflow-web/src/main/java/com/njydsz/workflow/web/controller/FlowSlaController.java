package com.njydsz.workflow.web.controller.analytics;

import org.springframework.validation.annotation.Validated;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.server.service.FlowSlaService;
import com.njydsz.workflow.server.service.FlowTaskService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
/**
 * SLA 超时自动策略 Controller
 *
 * <p>工作流 SLA（Service Level Agreement）监控与超时处理的 HTTP 入口。
 * 通过 {@code @XxlJob} 定时调度 + 管理后台手动触发双通道运行。
 *
 * <p><b>接口分组：</b>
 * <ul>
 *   <li><b>扫描</b>：{@code POST /sla/scan} — 手动触发 SLA 扫描（管理后台调试用，
 *       cronjob 默认每 60s 自动扫描）</li>
 *   <li><b>处理</b>：{@code POST /sla/process} — 单个任务 SLA 处理（按节点策略自动通过 / 驳回 / 仅提醒）</li>
 *   <li><b>配置</b>：{@code GET /sla/config/{nodeId}} / {@code POST /sla/config} — 节点级 SLA 配置</li>
 *   <li><b>统计</b>：{@code GET /sla/stats} — SLA 达成率 / 超时分布（报表）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>扫描 / 处理通过 {@link AuthApiPermission} 校验
 * {@link PermissionCodes#WORKFLOW_SLA_CONFIG} 权限码；幂等保护由 {@link Idempotent} 注解 5s 防重，
 * 防止并发扫描同一批任务。
 *
 * <p><b>限流：</b>扫描接口通过 {@link RateLimit} 50 QPS 限流，防止管理后台高频调用拖垮扫描器。
 *
 * <p><b>性能优化：</b>扫描器使用<b>游标分页</b>（{@code id > lastId} + {@code LIMIT 200}），
 * 避免 OFFSET 大值性能问题；处理动作单批 200 条事务，失败回滚不影响其它批次。
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传与权限校验；扫描 / 处理逻辑下沉到
 * {@link FlowSlaService}，任务查询委托 {@link FlowTaskService}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowSlaService SLA 服务
 * @see FlowTaskService 任务服务
 * @see FlowRunTask 任务实体
 */
@Slf4j
@RestController
@Tag(name = "workflow-sla", description = "工作流 SLA 接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowSlaController {

    /** P1-6: SLA 超时自动策略服务 */
    private final FlowSlaService slaService;
    /** 任务服务（slaProcess 中按 id 查任务） */
    private final FlowTaskService taskService;

    /**
     * P1-6: 手动触发 SLA 扫描（管理后台调试用，cronjob 默认每 60s 自动扫描）
     *
     * @return 本轮扫描处理的任务数
     */
    @Idempotent(key = "ydsz:workflow:FlowSlaController:slaScan:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowsla.slaScan", threshold = 50)
    @PostMapping("/sla/scan")
    @Audit(module = "SLA管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'slaScan'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_SLA_CONFIG)
    public BaseResponse<Integer> slaScan() {
        int processed = slaService.scanAndProcess();
        return BaseResponse.success(processed);
    }

    /**
     * P1-6: 手动触发单条任务的 SLA 处理
     *
     * @param taskId 任务 ID
     * @return 是否处理成功
     */
    @Idempotent(key = "ydsz:workflow:FlowSlaController:slaProcess:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowsla.slaProcess", threshold = 50)
    @PostMapping("/sla/process/{taskId}")
    @Audit(module = "SLA管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'slaProcess'")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_SLA_CONFIG)
    public BaseResponse<Boolean> slaProcess(@PathVariable String taskId) {
        FlowRunTask task = taskService.getById(taskId);
        if (task == null) {
            return BaseResponse.error(BaseResultCode.NOT_FOUND, "任务不存在: " + taskId);
        }
        boolean ok = slaService.processOverdue(task);
        return BaseResponse.success(ok);
    }
}
