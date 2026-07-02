package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.PaymentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 回款 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface PaymentMapper extends BaseMapper<PaymentDO> {

    PaymentDO selectByCode(@Param("code") String code);

    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("confirmedBy") Long confirmedBy);

    int updateAllocation(@Param("id") Long id,
                         @Param("allocation") String allocation,
                         @Param("allocatedAmount") BigDecimal allocatedAmount,
                         @Param("unallocatedAmount") BigDecimal unallocatedAmount);

    List<PaymentDO> selectByContract(@Param("contractId") Long contractId);

    List<PaymentDO> selectByCustomer(@Param("customerId") Long customerId);

    List<PaymentDO> selectUnallocated(@Param("customerId") Long customerId);

    BigDecimal sumReceivedByContract(@Param("contractId") Long contractId);

    List<Map<String, Object>> aggregateByMonth(@Param("initiationId") Long initiationId);

    List<Map<String, Object>> aggregateByCustomer();

    /** 跨项目汇总已分配（确认）回款金额 */
    BigDecimal sumAllocatedAmount();

    /** P6 每日对账：跨项目汇总已分配金额（兼容 sumAmountAllocated） */
    BigDecimal sumAmountAllocated();

    /**
     * 批次18：跨项目按月汇总已确认回款金额（最近 N 个月）
     *
     * <p>用于 KPI 趋势的"已确认收入"序列。
     * 返回字段：month / amount / cnt
     */
    List<Map<String, Object>> aggregateByRecentMonth(@Param("limit") Integer limit);
}
