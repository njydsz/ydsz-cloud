package com.njydsz.pmis.workflow.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 待办任务 Mapper
 *
 * <p>对应 pmis_flow_task 表，提供待办/已办查询、任务完成、会签计数、批量取消等能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowTaskMapper extends BaseMapper<FlowTaskDO> {

    /**
     * 根据实例 ID 查所有任务
     */
    List<FlowTaskDO> selectByInstanceId(@Param("instanceId") Long instanceId);

    /**
     * 查某实例的当前 PENDING 任务
     */
    List<FlowTaskDO> selectPendingByInstance(@Param("instanceId") Long instanceId);

    /**
     * 查某节点 PENDING 任务
     */
    List<FlowTaskDO> selectPendingByNode(@Param("instanceId") Long instanceId,
                                         @Param("nodeCode") String nodeCode);

    /**
     * 查用户的待办
     */
    List<FlowTaskDO> selectTodoByAssignee(@Param("assigneeId") String assigneeId,
                                          @Param("tenantId") Long tenantId);

    /**
     * 查用户的待办（真分页：LIMIT/OFFSET）
     *
     * @param assigneeId 办理人 ID
     * @param tenantId   租户 ID
     * @param offset     偏移量（从 0 开始）
     * @param limit      每页大小
     */
    List<FlowTaskDO> selectTodoByAssigneePage(@Param("assigneeId") String assigneeId,
                                              @Param("tenantId") Long tenantId,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    /**
     * 统计用户待办总数（用于分页计算总页数）
     */
    long countTodoByAssignee(@Param("assigneeId") String assigneeId,
                             @Param("tenantId") Long tenantId);

    /**
     * 查用户已办
     */
    List<FlowTaskDO> selectDoneByAssignee(@Param("assigneeId") String assigneeId,
                                          @Param("tenantId") Long tenantId);

    /**
     * 标记任务完成
     */
    int completeTask(@Param("id") Long id,
                     @Param("taskStatus") String taskStatus,
                     @Param("comment") String comment,
                     @Param("finishAt") java.time.LocalDateTime finishAt,
                     @Param("durationMs") Long durationMs);

    /**
     * 会签计数器 +1
     */
    int incrementFinished(@Param("id") Long id);

    /**
     * 取消某实例下所有 PENDING 任务
     */
    int cancelByInstance(@Param("instanceId") Long instanceId,
                         @Param("taskStatus") String taskStatus);

    /**
     * 跳过某节点剩余 PENDING（同会签场景）
     */
    int skipByNode(@Param("instanceId") Long instanceId,
                   @Param("nodeCode") String nodeCode,
                   @Param("taskStatus") String taskStatus);

    /**
     * P2-18: 冻结某实例下所有 PENDING/CLAIMED 任务（流程挂起时调用）
     *
     * @param instanceId 实例 ID
     */
    int freezeByInstance(@Param("instanceId") Long instanceId);

    /**
     * P2-18: 解冻某实例下所有 FROZEN 任务（流程激活时调用，回到 PENDING）
     *
     * @param instanceId 实例 ID
     */
    int unfreezeByInstance(@Param("instanceId") Long instanceId);

    /**
     * 统计某实例某节点的未完成任务数（用于并行网关 join 判断）
     */
    int countPendingByNode(@Param("instanceId") Long instanceId,
                           @Param("nodeCode") String nodeCode);

    /**
     * 更新会签计数（设置 approveFinished）
     */
    int updateApproveFinished(@Param("id") Long id,
                              @Param("approveFinished") Integer approveFinished);

    /**
     * 更新任务办理人信息（用于会签场景下多人共用一个任务时切换办理人）
     */
    int updateAssignee(@Param("id") Long id,
                       @Param("assigneeId") String assigneeId,
                       @Param("assigneeName") String assigneeName,
                       @Param("assigneeType") String assigneeType);
}
