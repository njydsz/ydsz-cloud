package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.InvoiceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 发票 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface InvoiceMapper extends BaseMapper<InvoiceDO> {

    /**
     * 按发票编码查询发票
     *
     * @param code 发票编码
     * @return 发票对象，未找到返回 null
     */
    InvoiceDO selectByCode(@Param("code") String code);

    /**
     * 按发票号查询发票
     *
     * @param invoiceNo 发票号
     * @return 发票对象，未找到返回 null
     */
    InvoiceDO selectByInvoiceNo(@Param("invoiceNo") String invoiceNo);

    /**
     * 更新发票状态
     *
     * @param id         发票 ID
     * @param status     目标状态
     * @param approvedBy 审批人 ID
     * @param issuedBy   开票人 ID
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id, @Param("status") String status,
                     @Param("approvedBy") String approvedBy,
                     @Param("issuedBy") String issuedBy);

    /**
     * 按合同 ID 查询发票列表
     *
     * @param contractId 合同 ID
     * @return 发票列表
     */
    List<InvoiceDO> selectByContract(@Param("contractId") String contractId);

    /**
     * 按立项 ID 查询发票列表
     *
     * @param initiationId 立项 ID
     * @return 发票列表
     */
    List<InvoiceDO> selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按客户 ID 查询发票列表
     *
     * @param customerId 客户 ID
     * @return 发票列表
     */
    List<InvoiceDO> selectByCustomer(@Param("customerId") String customerId);

    /**
     * 按合同汇总已开票金额
     *
     * @param contractId 合同 ID
     * @return 已开票金额
     */
    BigDecimal sumInvoicedByContract(@Param("contractId") String contractId);

    /**
     * 按状态聚合合同下的发票计数
     *
     * @param contractId 合同 ID
     * @return 状态聚合结果列表
     */
    List<Map<String, Object>> aggregateByStatus(@Param("contractId") String contractId);

    /**
     * 按月汇总开票金额
     *
     * @param initiationId 立项 ID
     * @return 月度汇总列表
     */
    List<Map<String, Object>> sumByMonth(@Param("initiationId") String initiationId);

    /**
     * 跨合同汇总已开票金额
     *
     * @return 已开票总额
     */
    BigDecimal sumInvoicedAmount();

    /**
     * P6 每日对账：跨合同汇总已开票金额（ISSUED 状态，兼容 sumAmountIssued）
     *
     * @return ISSUED 状态已开票总额
     */
    BigDecimal sumAmountIssued();

    /**
     * 跨合同汇总已确认收入（ALLOCATED 状态的 payment）
     *
     * @return 已确认收入总额
     */
    BigDecimal sumConfirmedAmount();

    /**
     * 客户维度下钻
     *
     * @return 客户维度开票汇总列表
     */
    List<Map<String, Object>> sumByCustomer();

    /**
     * 部门维度下钻
     *
     * @return 部门维度开票汇总列表
     */
    List<Map<String, Object>> sumByDepartment();

    /**
     * 项目类型维度下钻
     *
     * <p>JOIN pmis_project_initiation 获取项目类型，按项目类型聚合开票金额。
     * 返回字段：project_type / cnt / total_amount
     *
     * @return 项目类型维度开票汇总列表
     */
    List<Map<String, Object>> sumByProjectType();

    /**
     * 统计独立项目数
     *
     * @return 独立项目数
     */
    Integer countDistinctInitiation();

    /**
     * 按年度汇总合同开票金额（P2-4 驾驶舱合同总额年度趋势）
     *
     * <p>从 invoice_date（按 YYYY）聚合 ISSUED 状态的合同开票金额；
     * 返回字段：year / totalAmount / invoiceCount / projectCount
     */
    List<Map<String, Object>> sumByYear();

    /**
     * 批次18 增量：按月汇总开票金额（最近 N 个月）
     *
     * <p>从 invoice_date（按 YYYY-MM）聚合 ISSUED + NORMAL 状态的开票金额；
     * 返回字段：month / total_amount / invoice_count
     *
     * @param limit 限定返回最近的 N 个月（默认 12，上限 36）
     */
    List<Map<String, Object>> sumByRecentMonth(@Param("limit") Integer limit);
}
