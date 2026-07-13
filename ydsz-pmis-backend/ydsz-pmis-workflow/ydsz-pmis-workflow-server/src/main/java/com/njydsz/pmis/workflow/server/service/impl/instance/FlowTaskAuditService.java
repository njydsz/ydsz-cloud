package com.njydsz.pmis.workflow.server.service.impl.instance;

import com.njydsz.pmis.workflow.domain.entity.FlowAuditLogDO;
import com.njydsz.pmis.workflow.domain.entity.FlowDelegateAuthDO;
import com.njydsz.pmis.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.infra.mapper.FlowAuditLogMapper;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowInstanceMapper;
import com.njydsz.pmis.workflow.server.service.FlowDelegateAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 任务审计/委派代理日志服务
 *
 * <p>从 {@code FlowTaskCompleteServiceImpl} 拆分的"代理操作审计"职责。
 * 委派代理日志已合并到 {@code pmis_flow_audit_log}（businessType=DELEGATE_PROXY），
 * 不再使用独立的 {@code pmis_flow_delegate_log} 表。
 *
 * <p>审计日志字段映射：
 * <ul>
 *   <li>businessType = "DELEGATE_PROXY" — 标识委派代理操作</li>
 *   <li>action = PASS/REJECT/CLAIM/... — 实际办理动作</li>
 *   <li>operatorId = 代理人 ID</li>
 *   <li>targetId = 授权人 ID</li>
 *   <li>comment = 办理意见</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskAuditService {

    /** 委派代理操作的业务类型标识 */
    public static final String BIZ_TYPE_DELEGATE_PROXY = "DELEGATE_PROXY";

    private final FlowAuditLogMapper auditLogMapper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowDelegateAuthService delegateAuthService;

    /**
     * 记录委派代理操作日志（CLAIM/DELEGATE_RETURN/PASS 等场景）。
     *
     * <p>当任务不是代理场景（无 assignorId 或 assigneeId 不含数字）时
     * 静默返回，不抛异常；写日志本身失败也仅 warn 提示，不影响主流程。
     *
     * @param task   当前任务（assignorId=授权人，assigneeId=被委派人）
     * @param action 动作类型（CLAIM/PASS/DELEGATE_RETURN/...）
     * @param opType 操作类型：ACT=办理 / VIEW=查看（已废弃，保留参数兼容）
     */
    public void logDelegateOperation(FlowRunTaskDO task, String action, String opType) {
        if (task == null || auditLogMapper == null) {
            return;
        }
        try {
            String ownerId = task.getAssignorId();
            String delegateId = parseAssignorId(task.getAssigneeId());
            if (ownerId == null || delegateId == null) {
                return; // 非代理场景
            }
            FlowAuditLogDO logEntry = new FlowAuditLogDO();
            logEntry.setTenantId(task.getTenantId());
            logEntry.setInstanceId(task.getInstanceId());
            logEntry.setTaskId(task.getId());
            logEntry.setNodeCode(task.getNodeCode());
            logEntry.setBusinessType(BIZ_TYPE_DELEGATE_PROXY);
            logEntry.setAction(action);
            logEntry.setOperatorId(delegateId);
            logEntry.setTargetId(ownerId);
            logEntry.setComment(task.getComment());
            logEntry.setOperatedAt(LocalDateTime.now());
            logEntry.setProviderTraceId(task.getProviderTraceId());
            LocalDateTime now = LocalDateTime.now();
            logEntry.setCreatedAt(now);
            logEntry.setUpdatedAt(now);
            auditLogMapper.insert(logEntry);
        } catch (Exception e) {
            FlowTaskAuditService.log.warn("[Flow] 委派代理日志写入失败: taskId={} err={}",
                    task.getId(), e.getMessage());
        }
    }

    /**
     * 解析 assigneeId 中的真实用户 ID。
     *
     * <p>仅当 assigneeId 是纯数字时返回其值（系统用户 ID 场景），否则返回 null
     * 表示"非代理场景"。
     */
    private String parseAssignorId(String assigneeId) {
        if (assigneeId == null || !assigneeId.matches("\\d+")) {
            return null;
        }
        return assigneeId;
    }

    /**
     * 解析委派授权 ID。
     *
     * <p>查询当前任务实例，按租户/授权人/流程/节点匹配最合适的授权规则，
     * 用于审计日志中关联授权记录。
     */
    @SuppressWarnings("unused")
    private String resolveDelegateAuthId(FlowRunTaskDO task, String ownerId) {
        try {
            FlowInstanceDO instance = instanceMapper.selectById(task.getInstanceId());
            if (instance == null) {
                return null;
            }
            FlowDelegateAuthDO matched = delegateAuthService.matchAuth(
                    instance.getTenantId(), ownerId,
                    instance.getFlowCode(), task.getNodeCode());
            return matched != null ? matched.getId() : null;
        } catch (Exception e) {
            FlowTaskAuditService.log.debug("[Flow] 委派 authId 解析失败: taskId={} err={}",
                    task.getId(), e.getMessage());
            return null;
        }
    }
}
