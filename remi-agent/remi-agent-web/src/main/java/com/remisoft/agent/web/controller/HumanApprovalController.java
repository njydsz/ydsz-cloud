package com.remisoft.agent.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.remisoft.agent.server.agent.HumanApprovalService;
import com.remisoft.agent.server.agent.HumanApprovalService.ApprovalRequest;
import com.remisoft.common.core.response.BaseResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.remisoft.common.audit.annotation.Audit;
import com.remisoft.common.audit.enums.AuditAction;
import com.remisoft.common.audit.enums.AuditType;
import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.agent.domain.enums.AgentResultCode;

/**
 * Human-in-the-Loop 人工审批 REST API Controller。
 *
 * <p>提供 Agent 执行过程中"高风险操作需人工审批"场景下的审批管理接口，是 Agent 安全合规的关键环节：
 * <ul>
 *   <li>{@code GET /agent/approvals/pending} - 列出所有待审批请求</li>
 *   <li>{@code GET /agent/approvals/{id}} - 获取审批请求详情</li>
 *   <li>{@code POST /agent/approvals/{id}/approve} - 审批通过</li>
 *   <li>{@code POST /agent/approvals/{id}/reject} - 审批拒绝</li>
 * </ul>
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>Agent 准备执行"删除线上数据"等高危操作时，触发人工审批</li>
 *   <li>Agent 准备调用"对外发送通知"等敏感工具时，需管理员确认</li>
 *   <li>Agent 生成的对外内容（如公告/合同）需人工 review 后才能发出</li>
 * </ul>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>待审批列表查询：供审批人工作台展示</li>
 *   <li>审批通过/拒绝：触发 {@link HumanApprovalService} 通知 Agent 继续/中止执行</li>
 *   <li>审批意见记录：审批人可填写 comment 留痕</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）</li>
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志（操作人 + 审批结果留痕）</li>
 *   <li>已审批/已拒绝的请求二次调用会返回 error（{@code Approval not found or already resolved}）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/approvals")
@RequiredArgsConstructor
public class HumanApprovalController {

    /** 人工审批服务（封装审批请求的增删改查 + Agent 通知） */
    private final HumanApprovalService approvalService;

    /**
     * 列出所有待审批请求。
     *
     * <p>返回状态为 PENDING 的所有审批请求（按创建时间倒序），供审批人工作台展示。
     * 已审批/已拒绝的请求不在此接口返回。
     *
     * @return 统一响应结果，data 为 {@link ApprovalRequest} 列表
     */
    @Audit(module = "人工审批", type = AuditType.OPERATION, action = AuditAction.QUERY, content = "'listPending'")
    @GetMapping("/pending")
    public BaseResponse<List<ApprovalRequest>> listPending() {
        return BaseResponse.success(approvalService.listPending());
    }

    /**
     * 获取审批请求详情。
     *
     * @param id 审批请求 ID
     * @return 统一响应结果，data 为审批请求详情；不存在时返回 error 响应
     */
    @Audit(module = "人工审批", type = AuditType.OPERATION, action = AuditAction.QUERY, content = "'getApproval: ' + #id")
    @GetMapping("/{id}")
    public BaseResponse<ApprovalRequest> getApproval(@PathVariable String id) {
        ApprovalRequest request = approvalService.getApproval(id);
        if (request == null) {
            return BaseResponse.error(AgentResultCode.AGENT_NOT_FOUND, "Approval not found: " + id);
        }
        return BaseResponse.success(request);
    }

    /**
     * 审批通过。
     *
     * <p>将指定 ID 的审批请求标记为 APPROVED，并通过 {@link HumanApprovalService} 通知对应的 Agent
     * 继续执行。已审批/已拒绝的请求二次调用会返回 error。
     *
     * @param id       审批请求 ID
     * @param approver 审批人标识（可选，建议从当前用户上下文获取）
     * @param comment  审批意见（可选）
     * @return 统一响应结果，data 为 true 表示审批成功；false 表示请求不存在或已处理
     */
    @Audit(module = "人工审批", type = AuditType.OPERATION, action = AuditAction.APPROVE, content = "'approve'")
    @Idempotent(key = "remi:agent:HumanApprovalController:approve:lock", ttlSeconds = 5)
    @PostMapping("/{id}/approve")
    public BaseResponse<Boolean> approve(@PathVariable String id,
                                          @RequestParam(required = false) String approver,
                                          @RequestParam(required = false) String comment) {
        log.info("[HumanApproval] 审批通过: id={}, approver={}", id, approver);
        boolean result = approvalService.approve(id, approver, comment);
        if (!result) {
            return BaseResponse.error(AgentResultCode.AGENT_NOT_FOUND, "Approval not found or already resolved: " + id);
        }
        return BaseResponse.success(true);
    }

    /**
     * 审批拒绝。
     *
     * <p>将指定 ID 的审批请求标记为 REJECTED，并通过 {@link HumanApprovalService} 通知对应的 Agent
     * 中止执行或走预设的拒绝分支。已审批/已拒绝的请求二次调用会返回 error。
     *
     * @param id       审批请求 ID
     * @param approver 审批人标识（可选）
     * @param comment  拒绝原因/意见（可选，建议必填便于审计追溯）
     * @return 统一响应结果，data 为 true 表示拒绝成功；false 表示请求不存在或已处理
     */
    @Audit(module = "人工审批", type = AuditType.OPERATION, action = AuditAction.REJECT, content = "'reject'")
    @Idempotent(key = "remi:agent:HumanApprovalController:reject:lock", ttlSeconds = 5)
    @PostMapping("/{id}/reject")
    public BaseResponse<Boolean> reject(@PathVariable String id,
                                         @RequestParam(required = false) String approver,
                                         @RequestParam(required = false) String comment) {
        log.info("[HumanApproval] 审批拒绝: id={}, approver={}, reason={}", id, approver, comment);
        boolean result = approvalService.reject(id, approver, comment);
        if (!result) {
            return BaseResponse.error(AgentResultCode.AGENT_NOT_FOUND, "Approval not found or already resolved: " + id);
        }
        return BaseResponse.success(true);
    }
}
