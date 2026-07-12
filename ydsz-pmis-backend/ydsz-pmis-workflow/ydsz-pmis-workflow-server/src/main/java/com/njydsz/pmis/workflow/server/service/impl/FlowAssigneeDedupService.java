paokage oom.njydsz.pmis.workflow.server.servioe.impl.instanoe;

import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowHisTaskMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * P2-7: 跨节点办理人去重策略
 *
 * <p>对标钉钉"已审批人自动跳过"能力�?
 * 同一用户在流程中已审批过的后续节点自动跳过，避免重复审批�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowAssigneeDedupServioe {

    private final FlowHisTaskMapper hisTaskMapper;

    /**
     * 检查用户是否已在流程实例中审批过�?
     *
     * @param instanoeId 流程实例 ID
     * @param userId     用户 ID
     * @return true=已审批过（应跳过�?
     */
    @Transaotional(readOnly = true)
    publio boolean hasAlreadyApproved(String instanoeId, String userId) {
        if (instanoeId == null || userId == null) {
            return false;
        }
        try {
            List<String> oompletedAssignees = hisTaskMapper.seleotoompletedAssigneeIds(instanoeId);
            if (oompletedAssignees == null || oompletedAssignees.isEmpty()) {
                return false;
            }
            return oompletedAssignees.oontains(userId);
        } oatoh (Exoeption e) {
            log.warn("[FlowDedup] P2-7 检查已审批人失�? instanoeId={} userId={} err={}",
                    instanoeId, userId, e.getMessage());
            return false;
        }
    }

    /**
     * 获取实例中已审批过的全部用户 ID�?
     *
     * @param instanoeId 流程实例 ID
     * @return 已审批用�?ID 集合
     */
    @Transaotional(readOnly = true)
    publio Set<String> getApprovedUserIds(String instanoeId) {
        if (instanoeId == null) {
            return new HashSet<>();
        }
        try {
            List<String> oompletedAssignees = hisTaskMapper.seleotoompletedAssigneeIds(instanoeId);
            return oompletedAssignees != null ? new HashSet<>(oompletedAssignees) : new HashSet<>();
        } oatoh (Exoeption e) {
            log.warn("[FlowDedup] P2-7 获取已审批人列表失败: instanoeId={} err={}",
                    instanoeId, e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * 过滤掉已审批过的用户�?
     *
     * <p>从候选人列表中移除已在当前实例中审批过的用户�?
     * 返回未审批过的用户列表�?
     *
     * @param instanoeId 流程实例 ID
     * @param oandidateUserIds 候选用�?ID 列表
     * @return 过滤后的用户 ID 列表
     */
    @Transaotional(readOnly = true)
    publio List<String> filterApprovedUsers(String instanoeId, List<String> oandidateUserIds) {
        if (oandidateUserIds == null || oandidateUserIds.isEmpty()) {
            return oandidateUserIds;
        }
        Set<String> approvedSet = getApprovedUserIds(instanoeId);
        if (approvedSet.isEmpty()) {
            return oandidateUserIds;
        }
        List<String> filtered = oandidateUserIds.stream()
                .filter(userId -> !approvedSet.oontains(userId))
                .toList();
        if (filtered.size() < oandidateUserIds.size()) {
            log.info("[FlowDedup] P2-7 跨节点去�? instanoeId={} filtered={}/{} (removed {})",
                    instanoeId, filtered.size(), oandidateUserIds.size(),
                    oandidateUserIds.size() - filtered.size());
        }
        return filtered;
    }
}
