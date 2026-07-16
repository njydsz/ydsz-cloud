package com.njydsz.pmis.workflow.server.service.impl.instance;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.pmis.workflow.infra.mapper.FlowHisTaskMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-7: 跨节点办理人去重策略
 *
 * <p>对标钉钉"已审批人自动跳过"能力。
 * 同一用户在流程中已审批过的后续节点自动跳过，避免重复审批。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAssigneeDedupService {

    private final FlowHisTaskMapper hisTaskMapper;

    /**
     * 检查用户是否已在流程实例中审批过。
     *
     * @param instanceId 流程实例 ID
     * @param userId     用户 ID
     * @return true=已审批过（应跳过）
     */
    @Transactional(readOnly = true)
    public boolean hasAlreadyApproved(String instanceId, String userId) {
        if (instanceId == null || userId == null) {
            return false;
        }
        try {
            List<String> completedAssignees = hisTaskMapper.selectCompletedAssigneeIds(instanceId);
            if (completedAssignees == null || completedAssignees.isEmpty()) {
                return false;
            }
            return completedAssignees.contains(userId);
        } catch (Exception e) {
            log.warn("[FlowDedup] P2-7 检查已审批人失败: instanceId={} userId={} err={}",
                    instanceId, userId, e.getMessage());
            return false;
        }
    }

    /**
     * 获取实例中已审批过的全部用户 ID。
     *
     * @param instanceId 流程实例 ID
     * @return 已审批用户 ID 集合
     */
    @Transactional(readOnly = true)
    public Set<String> getApprovedUserIds(String instanceId) {
        if (instanceId == null) {
            return new HashSet<>();
        }
        try {
            List<String> completedAssignees = hisTaskMapper.selectCompletedAssigneeIds(instanceId);
            return completedAssignees != null ? new HashSet<>(completedAssignees) : new HashSet<>();
        } catch (Exception e) {
            log.warn("[FlowDedup] P2-7 获取已审批人列表失败: instanceId={} err={}",
                    instanceId, e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * 过滤掉已审批过的用户。
     *
     * <p>从候选人列表中移除已在当前实例中审批过的用户，
     * 返回未审批过的用户列表。
     *
     * @param instanceId 流程实例 ID
     * @param candidateUserIds 候选用户 ID 列表
     * @return 过滤后的用户 ID 列表
     */
    @Transactional(readOnly = true)
    public List<String> filterApprovedUsers(String instanceId, List<String> candidateUserIds) {
        if (candidateUserIds == null || candidateUserIds.isEmpty()) {
            return candidateUserIds;
        }
        Set<String> approvedSet = getApprovedUserIds(instanceId);
        if (approvedSet.isEmpty()) {
            return candidateUserIds;
        }
        List<String> filtered = candidateUserIds.stream()
                .filter(userId -> !approvedSet.contains(userId))
                .toList();
        if (filtered.size() < candidateUserIds.size()) {
            log.info("[FlowDedup] P2-7 跨节点去重: instanceId={} filtered={}/{} (removed {})",
                    instanceId, filtered.size(), candidateUserIds.size(),
                    candidateUserIds.size() - filtered.size());
        }
        return filtered;
    }
}
