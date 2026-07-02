package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程实例 Mapper
 *
 * <p>对应 pmis_flow_instance 表，提供按业务关联查询、状态推进、发起人维度查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowInstanceMapper extends BaseMapper<FlowInstanceDO> {

    /**
     * 根据业务关联查实例
     */
    FlowInstanceDO selectByBusiness(@Param("businessType") String businessType,
                                    @Param("businessId") String businessId);

    /**
     * 状态更新
     */
    int updateStatus(@Param("id") Long id,
                     @Param("flowStatus") String flowStatus,
                     @Param("currentNodeCode") String currentNodeCode,
                     @Param("currentNodeName") String currentNodeName,
                     @Param("endAt") java.time.LocalDateTime endAt,
                     @Param("durationMs") Long durationMs);

    /**
     * P2-18: 更新流程变量 JSON（用于持久化 terminate reason 等元信息）
     *
     * @param id       实例 ID
     * @param variable 流程变量 JSON
     */
    int updateVariable(@Param("id") Long id,
                       @Param("variable") String variable);

    /**
     * 发起人维度查询
     */
    List<FlowInstanceDO> selectByInitiator(@Param("initiatorId") Long initiatorId,
                                           @Param("flowStatus") String flowStatus);

    /**
     * P2-23: 实例多维分页查询
     *
     * @param businessType 业务类型（可选）
     * @param initiatorId  发起人 ID（可选）
     * @param flowStatus   流程状态（可选）
     * @param startTime    开始时间下界（可选）
     * @param endTime      开始时间上界（可选）
     * @param tenantId     租户 ID（可选）
     * @param offset       偏移量（从 0 开始）
     * @param limit        每页大小
     * @return 实例列表
     */
    List<FlowInstanceDO> selectPage(@Param("businessType") String businessType,
                                    @Param("initiatorId") Long initiatorId,
                                    @Param("flowStatus") String flowStatus,
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime,
                                    @Param("tenantId") Long tenantId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    /**
     * P2-23: 实例多维分页计数
     *
     * @param businessType 业务类型（可选）
     * @param initiatorId  发起人 ID（可选）
     * @param flowStatus   流程状态（可选）
     * @param startTime    开始时间下界（可选）
     * @param endTime      开始时间上界（可选）
     * @param tenantId     租户 ID（可选）
     * @return 总数
     */
    long countPage(@Param("businessType") String businessType,
                   @Param("initiatorId") Long initiatorId,
                   @Param("flowStatus") String flowStatus,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime,
                   @Param("tenantId") Long tenantId);

    /**
     * 更新实例的 dueAt 字段（子流程超时用）
     *
     * @param id    实例 ID
     * @param dueAt 超时时间
     */
    int updateDueAt(@Param("id") Long id,
                    @Param("dueAt") LocalDateTime dueAt);
}
