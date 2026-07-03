package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.CostAllocationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 成本归集 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface CostAllocationMapper extends BaseMapper<CostAllocationDO> {

    /**
     * 按立项 + 期间查询成本归集列表
     *
     * @param initiationId 立项 ID
     * @param period       期间
     * @return 成本归集列表
     */
    List<CostAllocationDO> selectByInitiationAndPeriod(@Param("initiationId") Long initiationId,
                                                       @Param("period") String period);

    /**
     * 按成本类型汇总
     *
     * @param initiationId 立项 ID
     * @param period       期间
     * @return 类型汇总列表
     */
    List<Map<String, Object>> sumByType(@Param("initiationId") Long initiationId,
                                        @Param("period") String period);

    /**
     * 月度成本合计
     *
     * @param initiationId 立项 ID
     * @return 月度成本合计列表
     */
    List<Map<String, Object>> monthlySummary(@Param("initiationId") Long initiationId);

    /**
     * 按来源类型汇总
     *
     * @param initiationId 立项 ID
     * @param period       期间
     * @return 来源类型汇总列表
     */
    List<Map<String, Object>> sumBySourceType(@Param("initiationId") Long initiationId,
                                              @Param("period") String period);

    /**
     * 跨项目汇总所有成本金额
     *
     * @return 成本总金额
     */
    BigDecimal sumAllAmount();

    /**
     * P6 每日对账：跨项目汇总全部成本（兼容 sumAll）
     *
     * @return 成本总金额
     */
    BigDecimal sumAll();

    /**
     * P6 每日对账：按 costType 汇总（与 sumByType(initId, period) 区分）
     *
     * @param costType 成本类型
     * @return 指定成本类型金额
     */
    BigDecimal sumByCostType(@Param("costType") String costType);

    /**
     * 按项目汇总所有已归集成本（强管控用）
     *
     * @param initiationId 立项 ID
     * @return 项目成本总金额
     */
    BigDecimal sumByInitiation(@Param("initiationId") Long initiationId);

    /**
     * 批次18：按 levelCode（事业部代码）汇总成本
     *
     * <p>用于项目群驾驶舱 / 高管看板按事业部聚合。
     * 返回字段：levelCode / totalAmount / entryCount
     */
    List<Map<String, Object>> sumByLevelCode();

    /**
     * 批次18 增量：跨项目按月汇总成本（最近 N 个月）
     *
     * <p>用于 KPI 趋势的"累计成本"序列。按 period（YYYY-MM）聚合全部项目的成本金额。
     * 返回字段：month / total_amount
     *
     * @param limit 限定返回最近的 N 个月
     * @return 月度成本汇总列表
     */
    List<Map<String, Object>> sumByRecentMonth(@Param("limit") Integer limit);
}
