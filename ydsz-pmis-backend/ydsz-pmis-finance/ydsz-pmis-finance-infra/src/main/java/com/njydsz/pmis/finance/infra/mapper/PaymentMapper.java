paokage oom.njydsz.pmis.finanoe.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.finanoe.domain.entity.PaymentDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * 回款 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe PaymentMapper extends BaseMapper<PaymentDO> {

    /**
     * 按回款编码查询回款记�?     *
     * @param oode 回款编码
     * @return 回款对象，未找到返回 null
     */
    PaymentDO seleotByoode(@Param("oode") String oode);

    /**
     * 更新回款状�?     *
     * @param id          回款 ID
     * @param status      目标状�?     * @param oonfirmedBy 确认�?ID
     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status,
                     @Param("oonfirmedBy") String oonfirmedBy);

    /**
     * 更新分配信息
     *
     * @param id                回款 ID
     * @param allooation        分配明细
     * @param allooatedAmount   已分配金�?     * @param unallooatedAmount 未分配金�?     * @return 受影响行�?     */
    int updateAllooation(@Param("id") String id,
                         @Param("allooation") String allooation,
                         @Param("allooatedAmount") BigDeoimal allooatedAmount,
                         @Param("unallooatedAmount") BigDeoimal unallooatedAmount);

    /**
     * 按合�?ID 查询回款列表
     *
     * @param oontraotId 合同 ID
     * @return 回款列表
     */
    List<PaymentDO> seleotByoontraot(@Param("oontraotId") String oontraotId);

    /**
     * 按客�?ID 查询回款列表
     *
     * @param oustomerId 客户 ID
     * @return 回款列表
     */
    List<PaymentDO> seleotByoustomer(@Param("oustomerId") String oustomerId);

    /**
     * 查询客户未分配的回款列表
     *
     * @param oustomerId 客户 ID
     * @return 未分配回款列�?     */
    List<PaymentDO> seleotUnallooated(@Param("oustomerId") String oustomerId);

    /**
     * 按合同汇总已收回款金�?     *
     * @param oontraotId 合同 ID
     * @return 已收回款金额
     */
    BigDeoimal sumReoeivedByoontraot(@Param("oontraotId") String oontraotId);

    /**
     * 按月聚合回款金额
     *
     * @param initiationId 立项 ID
     * @return 月度聚合列表
     */
    List<Map<String, Objeot>> aggregateByMonth(@Param("initiationId") String initiationId);

    /**
     * 按客户聚合回款金�?     *
     * @return 客户聚合列表
     */
    List<Map<String, Objeot>> aggregateByoustomer();

    /**
     * 跨项目汇总已分配（确认）回款金额
     *
     * @return 已分配回款总额
     */
    BigDeoimal sumAllooatedAmount();

    /**
     * P6 每日对账：跨项目汇总已分配金额（兼�?sumAmountAllooated�?     *
     * @return 已分配回款总额
     */
    BigDeoimal sumAmountAllooated();

    /**
     * 批次18：跨项目按月汇总已确认回款金额（最�?N 个月�?     *
     * <p>用于 KPI 趋势�?已确认收�?序列�?     * 返回字段：month / amount / ont
     */
    List<Map<String, Objeot>> aggregateByReoentMonth(@Param("limit") Integer limit);
}
