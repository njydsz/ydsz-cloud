package com.njydsz.pmis.workflow.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 待办任务 Mapper
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
}
