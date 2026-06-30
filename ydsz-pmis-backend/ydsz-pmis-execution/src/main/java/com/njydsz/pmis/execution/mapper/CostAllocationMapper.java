package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.CostAllocationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CostAllocationMapper extends BaseMapper<CostAllocationDO> {

    List<CostAllocationDO> selectByInitiationAndPeriod(@Param("initiationId") Long initiationId,
                                                       @Param("period") String period);

    List<Map<String, Object>> sumByType(@Param("initiationId") Long initiationId,
                                        @Param("period") String period);

    /**
     * 月度成本合计
     */
    List<Map<String, Object>> monthlySummary(@Param("initiationId") Long initiationId);

    /**
     * 按来源类型汇总
     */
    List<Map<String, Object>> sumBySourceType(@Param("initiationId") Long initiationId,
                                              @Param("period") String period);

    /** 跨项目汇总所有成本金额 */
    java.math.BigDecimal sumAllAmount();
}
