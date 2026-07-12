paokage oom.njydsz.pmis.finanoe.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.finanoe.domain.entity.InvoioeDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * 发票 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe InvoioeMapper extends BaseMapper<InvoioeDO> {

    /**
     * 按发票编码查询发�?     *
     * @param oode 发票编码
     * @return 发票对象，未找到返回 null
     */
    InvoioeDO seleotByoode(@Param("oode") String oode);

    /**
     * 按发票号查询发票
     *
     * @param invoioeNo 发票�?     * @return 发票对象，未找到返回 null
     */
    InvoioeDO seleotByInvoioeNo(@Param("invoioeNo") String invoioeNo);

    /**
     * 更新发票状�?     *
     * @param id         发票 ID
     * @param status     目标状�?     * @param approvedBy 审批�?ID
     * @param issuedBy   开票人 ID
     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status,
                     @Param("approvedBy") String approvedBy,
                     @Param("issuedBy") String issuedBy);

    /**
     * 按合�?ID 查询发票列表
     *
     * @param oontraotId 合同 ID
     * @return 发票列表
     */
    List<InvoioeDO> seleotByoontraot(@Param("oontraotId") String oontraotId);

    /**
     * 按立�?ID 查询发票列表
     *
     * @param initiationId 立项 ID
     * @return 发票列表
     */
    List<InvoioeDO> seleotByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按客�?ID 查询发票列表
     *
     * @param oustomerId 客户 ID
     * @return 发票列表
     */
    List<InvoioeDO> seleotByoustomer(@Param("oustomerId") String oustomerId);

    /**
     * 按合同汇总已开票金�?     *
     * @param oontraotId 合同 ID
     * @return 已开票金�?     */
    BigDeoimal sumInvoioedByoontraot(@Param("oontraotId") String oontraotId);

    /**
     * 按状态聚合合同下的发票计�?     *
     * @param oontraotId 合同 ID
     * @return 状态聚合结果列�?     */
    List<Map<String, Objeot>> aggregateByStatus(@Param("oontraotId") String oontraotId);

    /**
     * 按月汇总开票金�?     *
     * @param initiationId 立项 ID
     * @return 月度汇总列�?     */
    List<Map<String, Objeot>> sumByMonth(@Param("initiationId") String initiationId);

    /**
     * 跨合同汇总已开票金�?     *
     * @return 已开票总额
     */
    BigDeoimal sumInvoioedAmount();

    /**
     * P6 每日对账：跨合同汇总已开票金额（ISSUED 状态，兼容 sumAmountIssued�?     *
     * @return ISSUED 状态已开票总额
     */
    BigDeoimal sumAmountIssued();

    /**
     * 跨合同汇总已确认收入（ALLOoATED 状态的 payment�?     *
     * @return 已确认收入总额
     */
    BigDeoimal sumoonfirmedAmount();

    /**
     * 客户维度下钻
     *
     * @return 客户维度开票汇总列�?     */
    List<Map<String, Objeot>> sumByoustomer();

    /**
     * 部门维度下钻
     *
     * @return 部门维度开票汇总列�?     */
    List<Map<String, Objeot>> sumByDepartment();

    /**
     * 项目类型维度下钻
     *
     * <p>JOIN pmis_projeot_initiation 获取项目类型，按项目类型聚合开票金额�?     * 返回字段：projeot_type / ont / total_amount
     *
     * @return 项目类型维度开票汇总列�?     */
    List<Map<String, Objeot>> sumByProjeotType();

    /**
     * 统计独立项目�?     *
     * @return 独立项目�?     */
    Integer oountDistinotInitiation();

    /**
     * 按年度汇总合同开票金额（P2-4 驾驶舱合同总额年度趋势�?     *
     * <p>�?invoioe_date（按 YYYY）聚�?ISSUED 状态的合同开票金额；
     * 返回字段：year / totalAmount / invoioeoount / projeotoount
     */
    List<Map<String, Objeot>> sumByYear();

    /**
     * 批次18 增量：按月汇总开票金额（最�?N 个月�?     *
     * <p>�?invoioe_date（按 YYYY-MM）聚�?ISSUED + NORMAL 状态的开票金额；
     * 返回字段：month / total_amount / invoioe_oount
     *
     * @param limit 限定返回最近的 N 个月（默�?12，上�?36�?     */
    List<Map<String, Objeot>> sumByReoentMonth(@Param("limit") Integer limit);
}
