package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程用户 Mapper
 *
 * <p>对应 pmis_flow_user 表，记录会签/或签场景下每个任务的处理人与处理状态。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowUserMapper extends BaseMapper<FlowUserDO> {

    /**
     * 查某 task 的所有用户
     */
    List<FlowUserDO> selectByTaskId(@Param("taskId") String taskId);

    /**
     * 标记用户已处理
     */
    int markProcessed(@Param("taskId") String taskId,
                      @Param("userId") String userId,
                      @Param("comment") String comment,
                      @Param("processAt") LocalDateTime processAt);

    /**
     * 查某实例某节点未处理的用户（会签场景）
     */
    List<FlowUserDO> selectUnprocessedByInstanceAndNode(@Param("instanceId") String instanceId,
                                                         @Param("nodeCode") String nodeCode);

    /**
     * 查某用户待办关联的任务 ID（通过 pmis_flow_user 表）
     */
    List<Long> selectTaskIdsByUser(@Param("userId") String userId,
                                   @Param("tenantId") String tenantId);

    /**
     * 批量插入
     */
    int batchInsert(@Param("list") List<FlowUserDO> list);
}
