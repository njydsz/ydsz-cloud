package com.remisoft.message.web.controller.core;

import jakarta.validation.Valid;

import com.remisoft.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.remisoft.common.auth.annotation.AuthApiPermission;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.common.permission.PermissionCodes;
import com.remisoft.message.domain.dto.core.OrchestrationFlowDTO;
import com.remisoft.message.domain.dto.core.OrchestrationResultVO;
import com.remisoft.message.server.service.core.OrchestrationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.remisoft.common.audit.annotation.Audit;
import com.remisoft.common.audit.enums.AuditAction;
import com.remisoft.common.audit.enums.AuditType;

/**
 * 消息编排引擎（Orchestration）Controller。
 *
 * <p>提供<b>消息发送流程的 DAG 编排</b>能力，是 P1-9「复杂通知编排」的核心入口。
 * 与单条消息发送不同，编排支持<b>多节点依赖、条件分支、失败策略</b>，
 * 可用于实现「审批通过 → 通知审批人 → 通知申请人 → 通知抄送人 → 触发下游单据」等复合流程。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/orchestration/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>执行编排流程</b>：{@code POST /execute} — 提交 DAG 定义并同步/异步执行，返回执行结果</li>
 * </ul>
 *
 * <p><b>DAG 节点：</b>编排流程由多个 {@code OrchestrationNode} 组成，每个节点可以是：
 * <ul>
 *   <li><b>消息节点</b>：发送一条消息（指定模板 / 接收人 / 通道）</li>
 *   <li><b>延时节点</b>：等待 N 秒 / 到指定时间后继续</li>
 *   <li><b>条件节点</b>：基于上一步执行结果判断分支</li>
 *   <li><b>回调节点</b>：调用外部 HTTP API（业务系统 webhook）</li>
 * </ul>
 *
 * <p><b>失败策略：</b>每个节点可配置 {@code onFailure}：
 * <ul>
 *   <li>{@code FAIL_FAST}：节点失败立即终止整个流程</li>
 *   <li>{@code CONTINUE}：节点失败不影响后续节点（适合非关键节点）</li>
 *   <li>{@code RETRY_N}：失败后自动重试 N 次</li>
 * </ul>
 *
 * <p><b>典型场景：</b>
 * <ul>
 *   <li>项目立项审批通过 → 通知审批人（站内信）+ 通知申请人（邮件）+ 通知财务（钉钉）</li>
 *   <li>订单支付成功 → 等 5 秒查询支付回执 → 通知用户 + 更新订单状态</li>
 *   <li>定时生日祝福：00:00 触发 → 查询当日生日用户 → 分批发送个性化祝福</li>
 * </ul>
 *
 * <p><b>多租户隔离：</b>所有编排按 {@code tenantId} 隔离，跨租户编排不可见。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口（execute）启用 {@link Idempotent} 5s 防重（避免重复执行同一 DAG）</li>
 *   <li>写接口（execute）启用 {@link RateLimit} 50 QPS 限流</li>
 *   <li>写接口（execute）启用 {@link Audit} 审计日志（异步持久化）</li>
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#NOTIF_MESSAGE_SEND} 权限码</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @see com.remisoft.message.server.service.core.OrchestrationService 编排服务
 * @see com.remisoft.message.domain.dto.core.OrchestrationFlowDTO 编排流程 DTO
 */
@Slf4j
@Tag(name = "消息编排", description = "DAG 流程编排引擎")
@RestController
@RequestMapping("/api/v1/message/orchestration")
@RequiredArgsConstructor
public class OrchestrationController {

    /** 消息编排服务 */
    private final OrchestrationService orchestrationService;

    /**
     * 执行 DAG 编排流程。
     *
     * @param flow 编排流程定义
     * @return 统一响应结果，包含编排执行结果
     */
    @Operation(summary = "执行编排流程")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "remi:message:OrchestrationController:execute:lock", ttlSeconds = 5)
    @Audit(module = "消息编排", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'execute'")
    @RateLimit(resource = "message.orchestration.execute", threshold = 50)
    @PostMapping("/execute")
    public BaseResponse<OrchestrationResultVO> execute(@Valid @RequestBody OrchestrationFlowDTO flow) {
        return BaseResponse.success(orchestrationService.execute(flow));
    }
}
