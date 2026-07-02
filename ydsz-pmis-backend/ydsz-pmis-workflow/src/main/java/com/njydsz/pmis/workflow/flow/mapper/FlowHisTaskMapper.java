package com.njydsz.pmis.workflow.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    /**
     * P2-31: 按节点统计平均耗时（GROUP BY node_code, node_name）
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID（可空）
     * @return 每个节点一行统计：nodeCode, nodeName, avgDurationMs, count
     */
    List<Map<String, Object>> nodeDurationStats(@Param("flowCode") String flowCode,
                                                 @Param("tenantId") Long tenantId);

    /**
     * P2-33: 多维筛选已办分页查询（真分页：LIMIT/OFFSET）
     *
     * @param assigneeId   办理人 ID（可空）
     * @param businessType 业务类型（可空）
     * @param flowCode     流程编码（可空）
     * @param startTime    完成时间下界（可空）
     * @param endTime      完成时间上界（可空）
     * @param tenantId     租户 ID（可空）
     * @param offset       偏移量（从 0 开始）
     * @param limit        每页大小
     */
    List<FlowHisTaskDO> selectDonePage(@Param("assigneeId") String assigneeId,
                                       @Param("businessType") String businessType,
                                       @Param("flowCode") String flowCode,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime,
                                       @Param("tenantId") Long tenantId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /**
     * P2-33: 多维筛选已办总数统计
     */
    long countDone(@Param("assigneeId") String assigneeId,
                   @Param("businessType") String businessType,
                   @Param("flowCode") String flowCode,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime,
                   @Param("tenantId") Long tenantId);
}
