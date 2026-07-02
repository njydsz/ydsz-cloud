package com.njydsz.pmis.workflow.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 历史任务 Mapper
 *
 * <p>对应 pmis_flow_his_task 表，归档已完成的流程任务，供已办查询与审计追溯。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowHisTaskMapper extends BaseMapper<FlowHisTaskDO> {

    /**
     * 查用户已办（历史）
     */
    List<FlowHisTaskDO> selectDoneByAssignee(@Param("assigneeId") String assigneeId,
                                             @Param("tenantId") Long tenantId);

    /**
     * 查用户已办（历史，真分页：LIMIT/OFFSET）
     *
     * @param assigneeId 办理人 ID
     * @param tenantId   租户 ID
     * @param offset     偏移量（从 0 开始）
     * @param limit      每页大小
     */
    List<FlowHisTaskDO> selectDoneByAssigneePage(@Param("assigneeId") String assigneeId,
                                                 @Param("tenantId") Long tenantId,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    /**
     * 统计用户已办总数（用于分页计算总页数）
     */
    long countDoneByAssignee(@Param("assigneeId") String assigneeId,
                             @Param("tenantId") Long tenantId);

    /**
     * 查某实例的所有历史
     */
    List<FlowHisTaskDO> selectByInstanceId(@Param("instanceId") Long instanceId);
}
