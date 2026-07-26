package com.njydsz.agent.server.agent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Human-in-the-Loop 审批服务
 *
 * <p>管理 Agent 执行过程中需要人工审批的暂停请求。支持：
 * <ul>
 *   <li>创建审批请求（Agent 执行到需要审批的步骤时暂停）</li>
 *   <li>列出待审批请求</li>
 *   <li>审批通过/拒绝</li>
 *   <li>根据审批结果恢复 Agent 执行</li>
 * </ul>
 *
 * <h3>对标竞品</h3>
 * <ul>
 *   <li>LangChain HumanInTheLoopCallback</li>
 *   <li>Dify 人工审批节点</li>
 *   <li>Coze 卡片交互</li>
 *   <li>AutoGen UserProxyAgent</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Service
public class HumanApprovalService {

    private static final Logger log = LoggerFactory.getLogger(HumanApprovalService.class);
    private static final int MAX_PENDING = 500;

    private final ConcurrentMap<String, ApprovalRequest> pendingApprovals = new ConcurrentHashMap<>();

    /**
     * 创建审批请求
     *
     * @param conversationId 对话 ID
     * @param traceId        执行链路 ID
     * @param stepDescription 当前步骤描述
     * @param context        上下文信息（用户输入、已有结果等）
     * @return 审批请求 ID
     */
    public String requestApproval(String conversationId, String traceId,
                                  String stepDescription, Map<String, Object> context) {
        if (pendingApprovals.size() >= MAX_PENDING) {
            evictExpired();
        }
        String approvalId = UUID.randomUUID().toString();
        ApprovalRequest request = new ApprovalRequest(
                approvalId, conversationId, traceId, stepDescription, context);
        pendingApprovals.put(approvalId, request);
        log.info("[HITL] 创建审批请求: id={}, convId={}, step={}",
                approvalId, conversationId, stepDescription);
        return approvalId;
    }

    /**
     * 获取待审批请求列表
     */
    public List<ApprovalRequest> listPending() {
        return pendingApprovals.values().stream()
                .filter(r -> r.getStatus() == ApprovalStatus.PENDING)
                .toList();
    }

    /**
     * 获取审批请求
     */
    public ApprovalRequest getApproval(String approvalId) {
        return pendingApprovals.get(approvalId);
    }

    /**
     * 审批通过
     */
    public boolean approve(String approvalId, String approver, String comment) {
        ApprovalRequest request = pendingApprovals.get(approvalId);
        if (request == null || request.getStatus() != ApprovalStatus.PENDING) {
            return false;
        }
        request.setStatus(ApprovalStatus.APPROVED);
        request.setApprover(approver);
        request.setComment(comment);
        request.setResolvedAt(LocalDateTime.now());
        log.info("[HITL] 审批通过: id={}, approver={}", approvalId, approver);
        return true;
    }

    /**
     * 审批拒绝
     */
    public boolean reject(String approvalId, String approver, String comment) {
        ApprovalRequest request = pendingApprovals.get(approvalId);
        if (request == null || request.getStatus() != ApprovalStatus.PENDING) {
            return false;
        }
        request.setStatus(ApprovalStatus.REJECTED);
        request.setApprover(approver);
        request.setComment(comment);
        request.setResolvedAt(LocalDateTime.now());
        log.info("[HITL] 审批拒绝: id={}, approver={}", approvalId, approver);
        return true;
    }

    /**
     * 检查审批状态
     */
    public ApprovalStatus getStatus(String approvalId) {
        ApprovalRequest request = pendingApprovals.get(approvalId);
        return request != null ? request.getStatus() : null;
    }

    /**
     * 清理过期的审批请求（超过 1 小时未处理）
     */
    private void evictExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        pendingApprovals.entrySet().removeIf(entry ->
                entry.getValue().getStatus() == ApprovalStatus.PENDING
                        && entry.getValue().getCreatedAt().isBefore(cutoff));
    }

    /**
     * 审批状态枚举
     */
    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED, EXPIRED
    }

    /**
     * 审批请求
     */
    public static class ApprovalRequest {
        private final String id;
        private final String conversationId;
        private final String traceId;
        private final String stepDescription;
        private final Map<String, Object> context;
        private final LocalDateTime createdAt;
        private volatile ApprovalStatus status;
        private volatile String approver;
        private volatile String comment;
        private volatile LocalDateTime resolvedAt;

        public ApprovalRequest(String id, String conversationId, String traceId,
                               String stepDescription, Map<String, Object> context) {
            this.id = id;
            this.conversationId = conversationId;
            this.traceId = traceId;
            this.stepDescription = stepDescription;
            this.context = context;
            this.createdAt = LocalDateTime.now();
            this.status = ApprovalStatus.PENDING;
        }

        public String getId() { return id; }
        public String getConversationId() { return conversationId; }
        public String getTraceId() { return traceId; }
        public String getStepDescription() { return stepDescription; }
        public Map<String, Object> getContext() { return context; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public ApprovalStatus getStatus() { return status; }
        public String getApprover() { return approver; }
        public String getComment() { return comment; }
        public LocalDateTime getResolvedAt() { return resolvedAt; }

        public void setStatus(ApprovalStatus status) { this.status = status; }
        public void setApprover(String approver) { this.approver = approver; }
        public void setComment(String comment) { this.comment = comment; }
        public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    }
}
