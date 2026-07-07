package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待办任务运行态 Mapper
 *
 * <p>对应 {@code pmis_flow_run_task} 表（原 {@code pmis_flow_task}，2026-07-06 重命名），
 * 提供待办/已办查询、任务完成、会签计数、批量取消等能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowRunTaskMapper extends BaseMapper<FlowRunTaskDO> {

    /**
     * 根据实例 ID 查所有任务
     */
    List<FlowRunTaskDO> selectByInstanceId(@Param("instanceId") String instanceId);

    /**
     * 查某实例的当前 PENDING 任务
     */
    List<FlowRunTaskDO> selectPendingByInstance(@Param("instanceId") String instanceId);

    /**
     * 查某节点 PENDING 任务
     */
    List<FlowRunTaskDO> selectPendingByNode(@Param("instanceId") String instanceId,
                                            @Param("nodeCode") String nodeCode);

    /**
     * 查用户的待办
     */
    List<FlowRunTaskDO> selectTodoByAssignee(@Param("assigneeId") String assigneeId,
                                             @Param("tenantId") String tenantId);

    /**
     * 查用户的待办（真分页：LIMIT/OFFSET）
     *
     * @param assigneeId 办理人 ID
     * @param tenantId   租户 ID
     * @param offset     偏移量（从 0 开始）
     * @param limit      每页大小
     */
    List<FlowRunTaskDO> selectTodoByAssigneePage(@Param("assigneeId") String assigneeId,
                                                 @Param("tenantId") String tenantId,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    /**
     * 统计用户待办总数（用于分页计算总页数）
     */
    long countTodoByAssignee(@Param("assigneeId") String assigneeId,
                             @Param("tenantId") String tenantId);

    /**
     * 查用户已办
     */
    List<FlowRunTaskDO> selectDoneByAssignee(@Param("assigneeId") String assigneeId,
                                             @Param("tenantId") String tenantId);

    /**
     * 标记任务完成
     */
    int completeTask(@Param("id") String id,
                     @Param("taskStatus") String taskStatus,
                     @Param("comment") String comment,
                     @Param("finishAt") LocalDateTime finishAt,
                     @Param("durationMs") Long durationMs);

    /**
     * 会签计数器 +1
     */
    int incrementFinished(@Param("id") String id);

    /**
     * 取消某实例下所有 PENDING 任务
     */
    int cancelByInstance(@Param("instanceId") String instanceId,
                         @Param("taskStatus") String taskStatus);

    /**
     * P0-1: 取消单个任务（边界事件触发时使用）
     *
     * @param id         任务 ID
     * @param taskStatus 目标状态
     * @param comment    取消原因
     */
    int cancelTask(@Param("id") String id,
                   @Param("taskStatus") String taskStatus,
                   @Param("comment") String comment);

    /**
     * 跳过某节点剩余 PENDING（同会签场景）
     */
    int skipByNode(@Param("instanceId") String instanceId,
                   @Param("nodeCode") String nodeCode,
                   @Param("taskStatus") String taskStatus);

    /**
     * P2-18: 冻结某实例下所有 PENDING/CLAIMED 任务（流程挂起时调用）
     *
     * @param instanceId 实例 ID
     */
    int freezeByInstance(@Param("instanceId") String instanceId);

    /**
     * P2-18: 解冻某实例下所有 FROZEN 任务（流程激活时调用，回到 PENDING）
     *
     * @param instanceId 实例 ID
     */
    int unfreezeByInstance(@Param("instanceId") String instanceId);

    /**
     * 统计某实例某节点的未完成任务数（用于并行网关 join 判断）
     */
    int countPendingByNode(@Param("instanceId") String instanceId,
                           @Param("nodeCode") String nodeCode);

    /**
     * 更新会签计数（设置 approveFinished）
     */
    int updateApproveFinished(@Param("id") String id,
                              @Param("approveFinished") Integer approveFinished);

    /**
     * 更新任务办理人信息（用于会签场景下多人共用一个任务时切换办理人）
     */
    int updateAssignee(@Param("id") String id,
                       @Param("assigneeId") String assigneeId,
                       @Param("assigneeName") String assigneeName,
                       @Param("assigneeType") String assigneeType);

    /**
     * P2-32: 查询超期任务（dueAt < now 且状态为 PENDING/CLAIMED）
     *
     * @param assigneeId 办理人 ID（可空，为空时查全部）
     * @param tenantId   租户 ID（可空）
     * @return 超期任务列表
     */
    List<FlowRunTaskDO> selectOverdue(@Param("assigneeId") String assigneeId,
                                      @Param("tenantId") String tenantId);

    /**
     * P2-32: 统计超期任务数量
     *
     * @param assigneeId 办理人 ID（可空，为空时统计全部）
     * @param tenantId   租户 ID（可空）
     * @return 超期任务数量
     */
    long countOverdue(@Param("assigneeId") String assigneeId,
                      @Param("tenantId") String tenantId);

    /**
     * P1-6: SLA 扫描 — 拉取所有设置了 dueAt 且未完成的任务（用于 SLA 调度器扫描）
     *
     * <p>扫描条件：task_status IN (PENDING, CLAIMED) AND due_at IS NOT NULL AND deleted = 0
     *
     * @param limit 单次扫描上限
     * @return 候选 SLA 任务列表
     */
    List<FlowRunTaskDO> selectSlaCandidates(@Param("limit") int limit);

    /**
     * P1-6: 增加 SLA 催办计数
     *
     * @param id             任务 ID
     * @param reminderCount  新的催办计数
     * @param lastRemindedAt 最近催办时间
     * @return 受影响行数
     */
    int incrementReminderCount(@Param("id") String id,
                               @Param("reminderCount") int reminderCount,
                               @Param("lastRemindedAt") LocalDateTime lastRemindedAt);

    /**
     * P1-6: 标记 SLA 动作（用于审计：AUTO_PASS / AUTO_REJECT / ESCALATE 等）
     */
    int markSlaAction(@Param("id") String id,
                      @Param("slaAction") String slaAction,
                      @Param("slaEscalated") Integer slaEscalated);
}
