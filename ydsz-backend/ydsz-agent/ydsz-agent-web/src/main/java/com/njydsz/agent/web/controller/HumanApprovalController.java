package com.njydsz.agent.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.server.agent.HumanApprovalService;
import com.njydsz.agent.server.agent.HumanApprovalService.ApprovalRequest;
import com.njydsz.common.core.response.BaseResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.vo.ApprovalRequestVO;

/**
 * Human-in-the-Loop 审批 REST API
 *
 * <p>提供 Agent 执行过程中人工审批的管理接口：
 * <ul>
 *   <li>{@code GET /agent/approvals/pending} — 列出待审批请求</li>
 *   <li>{@code GET /agent/approvals/{id}} — 获取审批请求详情</li>
 *   <li>{@code POST /agent/approvals/{id}/approve} — 审批通过</li>
 *   <li>{@code POST /agent/approvals/{id}/reject} — 审批拒绝</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/approvals")
@RequiredArgsConstructor
public class HumanApprovalController {

    private final HumanApprovalService approvalService;

    @GetMapping("/pending")
    public BaseResponse<List<ApprovalRequestVO>> listPending() {
        return BaseResponse.success(AgentConverter.INSTANT.approvalRequestListToVO(approvalService.listPending()));
    }

    @GetMapping("/{id}")
    public BaseResponse<ApprovalRequestVO> getApproval(@PathVariable String id) {
        ApprovalRequest request = approvalService.getApproval(id);
        if (request == null) {
            return BaseResponse.error("Approval not found: " + id);
        }
        return BaseResponse.success(request);
    }

    @Audit(module = "人工审批", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'approve'")
    @Idempotent(key = "ydsz:agent:HumanApprovalController:approve:lock", ttlSeconds = 5)
    @PostMapping("/{id}/approve")
    public BaseResponse<Boolean> approve(@PathVariable String id,
                                          @RequestParam(required = false) String approver,
                                          @RequestParam(required = false) String comment) {
        boolean result = approvalService.approve(id, approver, comment);
        if (!result) {
            return BaseResponse.error("Approval not found or already resolved: " + id);
        }
        return BaseResponse.success(true);
    }

    @Audit(module = "人工审批", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'reject'")
    @Idempotent(key = "ydsz:agent:HumanApprovalController:reject:lock", ttlSeconds = 5)
    @PostMapping("/{id}/reject")
    public BaseResponse<Boolean> reject(@PathVariable String id,
                                         @RequestParam(required = false) String approver,
                                         @RequestParam(required = false) String comment) {
        boolean result = approvalService.reject(id, approver, comment);
        if (!result) {
            return BaseResponse.error("Approval not found or already resolved: " + id);
        }
        return BaseResponse.success(true);
    }
}
