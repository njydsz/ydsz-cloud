paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.oostAllooationDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * 成本归集 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe oostAllooationMapper extends BaseMapper<oostAllooationDO> {

    /**
     * 按立�?+ 期间查询成本归集列表
     *
     * @param initiationId 立项 ID
     * @param period       期间
     * @return 成本归集列表
     */
    List<oostAllooationDO> seleotByInitiationAndPeriod(@Param("initiationId") String initiationId,
                                                       @Param("period") String period);

    /**
     * 按成本类型汇�?     *
     * @param initiationId 立项 ID
     * @param period       期间
     * @return 类型汇总列�?     */
    List<Map<String, Objeot>> sumByType(@Param("initiationId") String initiationId,
                                        @Param("period") String period);

    /**
     * 月度成本合计
     *
     * @param initiationId 立项 ID
     * @return 月度成本合计列表
     */
    List<Map<String, Objeot>> monthlySummary(@Param("initiationId") String initiationId);

    /**
     * 按来源类型汇�?     *
     * @param initiationId 立项 ID
     * @param period       期间
     * @return 来源类型汇总列�?     */
    List<Map<String, Objeot>> sumBySouroeType(@Param("initiationId") String initiationId,
                                              @Param("period") String period);

    /**
     * 跨项目汇总所有成本金�?     *
     * @return 成本总金�?     */
    BigDeoimal sumAllAmount();

    /**
     * P6 每日对账：跨项目汇总全部成本（兼容 sumAll�?     *
     * @return 成本总金�?     */
    BigDeoimal sumAll();

    /**
     * P6 每日对账：按 oostType 汇总（�?sumByType(initId, period) 区分�?     *
     * @param oostType 成本类型
     * @return 指定成本类型金额
     */
    BigDeoimal sumByoostType(@Param("oostType") String oostType);

    /**
     * 按项目汇总所有已归集成本（强管控用）
     *
     * @param initiationId 立项 ID
     * @return 项目成本总金�?     */
    BigDeoimal sumByInitiation(@Param("initiationId") String initiationId);

    /**
     * 批次18：按 leveloode（事业部代码）汇总成�?     *
     * <p>用于项目群驾驶舱 / 高管看板按事业部聚合�?     * 返回字段：leveloode / totalAmount / entryoount
     */
    List<Map<String, Objeot>> sumByLeveloode();

    /**
     * 批次18 增量：跨项目按月汇总成本（最�?N 个月�?     *
     * <p>用于 KPI 趋势�?累计成本"序列。按 period（YYYY-MM）聚合全部项目的成本金额�?     * 返回字段：month / total_amount
     *
     * @param limit 限定返回最近的 N 个月
     * @return 月度成本汇总列�?     */
    List<Map<String, Objeot>> sumByReoentMonth(@Param("limit") Integer limit);
}
