package com.njydsz.pmis.project.mapper.execution;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.execution.EvmMeasureDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * EVM 挣值度量 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface EvmMeasureMapper extends BaseMapper<EvmMeasureDO> {

    /**
     * 按立项 + WBS 任务 + 期间查询 EVM 度量记录
     *
     * @param initiationId 立项 ID
     * @param wbsTaskId    WBS 任务 ID
     * @param period       期间
     * @return EVM 度量记录，未找到返回 null
     */
    EvmMeasureDO selectByInitiationAndPeriod(@Param("initiationId") String initiationId,
                                             @Param("wbsTaskId") String wbsTaskId,
                                             @Param("period") String period);

    /**
     * 按立项 ID 查询 EVM 度量记录列表
     *
     * @param initiationId 立项 ID
     * @return EVM 度量记录列表
     */
    List<EvmMeasureDO> selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按 WBS 任务 ID 查询 EVM 度量记录列表
     *
     * @param wbsTaskId WBS 任务 ID
     * @return EVM 度量记录列表
     */
    List<EvmMeasureDO> selectByWbs(@Param("wbsTaskId") String wbsTaskId);

    /**
     * WBS 节点级偏差趋势：返回每期 CPI/SPI/VAC
     *
     * @param initiationId 立项 ID
     * @return 偏差趋势列表
     */
    List<Map<String, Object>> trendByPeriod(@Param("initiationId") String initiationId);

    /**
     * 跨项目 EVM 健康度：返回每个项目的 top_alert
     *
     * @return EVM 健康度聚合列表
     */
    List<Map<String, Object>> aggregateHealthByInitiation();
}
