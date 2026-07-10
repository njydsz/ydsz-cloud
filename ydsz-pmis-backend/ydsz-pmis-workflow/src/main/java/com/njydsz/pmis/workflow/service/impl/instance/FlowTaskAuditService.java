package com.njydsz.pmis.workflow.service.impl.instance;

import com.njydsz.pmis.workflow.entity.delegate.FlowDelegateAuthDO;
import com.njydsz.pmis.workflow.entity.delegate.FlowDelegateLogDO;
import com.njydsz.pmis.workflow.entity.instance.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.mapper.delegate.FlowDelegateLogMapper;
import com.njydsz.pmis.workflow.mapper.instance.FlowInstanceMapper;
import com.njydsz.pmis.workflow.service.delegate.FlowDelegateAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 任务审计/委派代理日志服务
 *
 * <p>从 {@code FlowTaskCompleteServiceImpl} 拆分的"代理操作审计"职责。
 * FlowTaskSupport 已提供通用 audit 方法（写入 pmis_flow_audit_log），本服务
 * 专门处理委派代理场景的扩展日志（写入 pmis_flow_delegate_log）。
 *
 * <p>委派代理日志用于追溯"谁在什么时间被代理处理了什么任务"，是审计追溯
 * 的重要补充（P1-4 引入）。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskAuditService {

    private final FlowDelegateLogMapper delegateLogMapper;
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
     * @param opType 操作类型：ACT=办理 / VIEW=查看
     */
    public void logDelegateOperation(FlowRunTaskDO task, String action, String opType) {
        if (task == null || delegateLogMapper == null) {
            return;
        }
        try {
            String ownerId = task.getAssignorId();
            String delegateId = parseAssignorId(task.getAssigneeId());
            if (ownerId == null || delegateId == null) {
                return; // 非代理场景
            }
            FlowDelegateLogDO log = new FlowDelegateLogDO();
            log.setTenantId(task.getTenantId());
            // P0-3 修复：重新匹配授权规则以获取 authId（不再硬编码 0L）
            log.setAuthId(resolveDelegateAuthId(task, ownerId));
            log.setInstanceId(task.getInstanceId());
            log.setTaskId(task.getId());
            log.setNodeCode(task.getNodeCode());
            log.setOwnerUserId(ownerId);
            log.setDelegateUserId(delegateId);
            log.setOpType(opType == null ? "ACT" : opType);
            log.setAction(action);
            log.setComment(task.getComment());
            log.setProviderTraceId(task.getProviderTraceId());
            LocalDateTime now = LocalDateTime.now();
            log.setCreatedAt(now);
            log.setUpdatedAt(now);
            delegateLogMapper.insert(log);
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
