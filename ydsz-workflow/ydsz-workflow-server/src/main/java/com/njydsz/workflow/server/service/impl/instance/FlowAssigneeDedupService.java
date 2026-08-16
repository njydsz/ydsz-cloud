package com.njydsz.workflow.server.service.impl.instance;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.njydsz.workflow.infra.mapper.FlowHisTaskMapper;

/**
 * P2-7: 跨节点办理人去重策略
 *
 * <p>对标钉钉「<b>已审批人自动跳过</b>」能力：当流程配置开启「同人不重复审批」开关时，
 * 同一用户在流程实例中已审批过的后续节点<b>自动跳过</b>，避免重复审批。
 * 是大厂 B 端工作流「合规化 + 提效」的标准能力。
 *
 * <p><b>业务场景：</b>
 * <ul>
 *   <li><b>效率提升</b>：典型场景「部门负责人审批 → 财务复核 → CEO 审批」，
 *       部门负责人与 CEO 是同一人时，CEO 节点自动跳过，避免重复审批</li>
 *   <li><b>合规性</b>：避免同一审批人多次出现在同一流程中导致「自己审批自己」</li>
 *   <li><b>缩短流程</b>：减少不必要的审批节点，提升流程流转效率</li>
 * </ul>
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>单点检查</b>：{@link #hasAlreadyApproved} — 检查用户是否已在当前实例中审批过</li>
 *   <li><b>批量获取</b>：{@link #getApprovedUserIds} — 获取当前实例已审批过的全部用户 ID 集合</li>
 *   <li><b>列表过滤</b>：{@link #filterApprovedUsers} — 从候选用户列表中过滤掉已审批用户，
 *       推送给会签 / 普通节点的「净候选列表」</li>
 * </ul>
 *
 * <p><b>数据来源：</b>{@code ydsz_flow_his_task} 表（已归档历史任务），
 * 任务完成后由 {@code FlowTaskArchiveService} 写入历史表。
 *
 * <p><b>事务边界：</b>类级别 {@code @Transactional(readOnly = true)}，
 * 所有方法走只读事务，配合 MySQL / PG 主从分离提升查询性能。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>空安全</b>：{@code instanceId} / {@code userId} 为 null 时直接返回「未审批过」</li>
 *   <li><b>异常降级</b>：查询异常时返回「未审批过」（不阻塞流程），仅记录 warn 日志</li>
 *   <li><b>性能优化</b>：{@code filterApprovedUsers} 一次性查询所有已审批人 + 应用层过滤，
 *       避免 N 次 DB 查询</li>
 *   <li><b>去重语义</b>：使用 {@link HashSet} 存储已审批人，{@code O(1)} 时间复杂度判断</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 场景：会签节点「财务部 5 人」审批，需要过滤已审批过的用户
 * List<String> candidates = Arrays.asList("1001", "1002", "1003", "1004", "1005");
 * List<String> filtered = dedupService.filterApprovedUsers(instanceId, candidates);
 * // filtered = ["1003", "1004", "1005"] （假设 1001, 1002 已审批过）
 * }</pre>
 *
 * <p><b>与流程设计器配合：</b>
 * <p>「跨节点去重」由流程定义节点上的 {@code skipSameUser} 字段控制：
 * <ul>
 *   <li>{@code skipSameUser = true} — 启用自动跳过（本服务生效）</li>
 *   <li>{@code skipSameUser = false}（默认）— 不启用，候选人不做去重</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowHisTaskMapper 历史任务 Mapper
 * @see FlowTaskArchiveService 任务归档服务（历史任务来源）
 * @see FlowInstanceService 流程实例服务（在创建下游任务时调用本服务过滤）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAssigneeDedupService {

    // ============================== 依赖注入 ==============================

    /** 历史任务 Mapper，负责 {@code ydsz_flow_his_task} 表的查询（已审批人来源） */
    private final FlowHisTaskMapper hisTaskMapper;

    // ============================== 单点检查 ==============================

    /**
     * 检查用户是否已在流程实例中审批过
     *
     * <p>用于会签 / 普通节点的「单点去重判断」。
     *
     * <p><b>性能优化：</b>走 {@code ydsz_flow_his_task} 索引 {@code idx_instance_assignee}，
     * {@code O(1)} 时间复杂度。
     *
     * @param instanceId 流程实例 ID
     * @param userId     用户 ID
     * @return {@code true} = 已审批过（应跳过），{@code false} = 未审批过（保留为候选人）
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

    // ============================== 批量操作 ==============================

    /**
     * 获取实例中已审批过的全部用户 ID 集合
     *
     * <p>返回 {@link HashSet} 类型，便于后续 {@code O(1)} 时间复杂度判断。
     * 异常时返回空集（不阻塞主流程）。
     *
     * @param instanceId 流程实例 ID
     * @return 已审批用户 ID 集合（{@link HashSet}，可能为空）
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
     * 过滤掉已审批过的用户
     *
     * <p>从候选人列表中移除已在当前实例中审批过的用户，
     * 返回<b>未审批过</b>的用户列表。
     *
     * <p><b>性能：</b>一次 DB 查询 + 应用层过滤，{@code O(N)} 时间复杂度。
     *
     * @param instanceId       流程实例 ID
     * @param candidateUserIds 候选用户 ID 列表
     * @return 过滤后的用户 ID 列表（保留原顺序，可能比入参小）
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
