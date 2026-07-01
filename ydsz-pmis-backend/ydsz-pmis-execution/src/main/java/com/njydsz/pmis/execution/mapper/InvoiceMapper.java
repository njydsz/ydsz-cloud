package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.InvoiceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface InvoiceMapper extends BaseMapper<InvoiceDO> {

    InvoiceDO selectByCode(@Param("code") String code);

    InvoiceDO selectByInvoiceNo(@Param("invoiceNo") String invoiceNo);

    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("approvedBy") Long approvedBy,
                     @Param("issuedBy") Long issuedBy);

    List<InvoiceDO> selectByContract(@Param("contractId") Long contractId);

    List<InvoiceDO> selectByInitiation(@Param("initiationId") Long initiationId);

    List<InvoiceDO> selectByCustomer(@Param("customerId") Long customerId);

    BigDecimal sumInvoicedByContract(@Param("contractId") Long contractId);

    List<Map<String, Object>> aggregateByStatus(@Param("contractId") Long contractId);

    List<Map<String, Object>> sumByMonth(@Param("initiationId") Long initiationId);

    /** 跨合同汇总已开票金额 */
    BigDecimal sumInvoicedAmount();

    /** P6 每日对账：跨合同汇总已开票金额（ISSUED 状态，兼容 sumAmountIssued） */
    BigDecimal sumAmountIssued();

    /** 跨合同汇总已确认收入（ALLOCATED 状态的 payment） */
    BigDecimal sumConfirmedAmount();

    /** 客户维度下钻 */
    List<Map<String, Object>> sumByCustomer();

    /** 部门维度下钻 */
    List<Map<String, Object>> sumByDepartment();

    /** 统计独立项目数 */
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
