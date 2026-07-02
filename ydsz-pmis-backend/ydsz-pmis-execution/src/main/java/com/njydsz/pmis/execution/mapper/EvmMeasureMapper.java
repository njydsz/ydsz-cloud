package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.EvmMeasureDO;
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

    EvmMeasureDO selectByInitiationAndPeriod(@Param("initiationId") Long initiationId,
                                             @Param("wbsTaskId") Long wbsTaskId,
                                             @Param("period") String period);

    List<EvmMeasureDO> selectByInitiation(@Param("initiationId") Long initiationId);

    List<EvmMeasureDO> selectByWbs(@Param("wbsTaskId") Long wbsTaskId);

    /** WBS 节点级偏差趋势：返回每期 CPI/SPI/VAC */
    List<Map<String, Object>> trendByPeriod(@Param("initiationId") Long initiationId);

    /** 跨项目 EVM 健康度：返回每个项目的 top_alert */
    List<Map<String, Object>> aggregateHealthByInitiation();
}
