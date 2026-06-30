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

    /** 跨合同汇总已确认收入（ALLOCATED 状态的 payment） */
    BigDecimal sumConfirmedAmount();

    /** 客户维度下钻 */
    List<Map<String, Object>> sumByCustomer();

    /** 部门维度下钻 */
    List<Map<String, Object>> sumByDepartment();

    /** 统计独立项目数 */
    Integer countDistinctInitiation();
}
