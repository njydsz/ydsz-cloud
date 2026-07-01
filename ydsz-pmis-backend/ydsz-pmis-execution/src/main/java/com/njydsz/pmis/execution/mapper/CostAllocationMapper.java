package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.CostAllocationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
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
    BigDecimal sumAllAmount();

    /** P6 每日对账：跨项目汇总全部成本（兼容 sumAll） */
    BigDecimal sumAll();

    /** P6 每日对账：按 costType 汇总（与 sumByType(initId, period) 区分） */
    BigDecimal sumByCostType(@Param("costType") String costType);

    /**
     * 按项目汇总所有已归集成本（强管控用）
     */
    BigDecimal sumByInitiation(@Param("initiationId") Long initiationId);

    /**
     * 批次18：按 levelCode（事业部代码）汇总成本
     *
     * <p>用于项目群驾驶舱 / 高管看板按事业部聚合。
     * 返回字段：levelCode / totalAmount / entryCount
     */
    List<Map<String, Object>> sumByLevelCode();
}
