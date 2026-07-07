package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.PaymentDO;
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

    /**
     * 按回款编码查询回款记录
     *
     * @param code 回款编码
     * @return 回款对象，未找到返回 null
     */
    PaymentDO selectByCode(@Param("code") String code);

    /**
     * 更新回款状态
     *
     * @param id          回款 ID
     * @param status      目标状态
     * @param confirmedBy 确认人 ID
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id, @Param("status") String status,
                     @Param("confirmedBy") Long confirmedBy);

    /**
     * 更新分配信息
     *
     * @param id                回款 ID
     * @param allocation        分配明细
     * @param allocatedAmount   已分配金额
     * @param unallocatedAmount 未分配金额
     * @return 受影响行数
     */
    int updateAllocation(@Param("id") String id,
                         @Param("allocation") String allocation,
                         @Param("allocatedAmount") BigDecimal allocatedAmount,
                         @Param("unallocatedAmount") BigDecimal unallocatedAmount);

    /**
     * 按合同 ID 查询回款列表
     *
     * @param contractId 合同 ID
     * @return 回款列表
     */
    List<PaymentDO> selectByContract(@Param("contractId") String contractId);

    /**
     * 按客户 ID 查询回款列表
     *
     * @param customerId 客户 ID
     * @return 回款列表
     */
    List<PaymentDO> selectByCustomer(@Param("customerId") String customerId);

    /**
     * 查询客户未分配的回款列表
     *
     * @param customerId 客户 ID
     * @return 未分配回款列表
     */
    List<PaymentDO> selectUnallocated(@Param("customerId") String customerId);

    /**
     * 按合同汇总已收回款金额
     *
     * @param contractId 合同 ID
     * @return 已收回款金额
     */
    BigDecimal sumReceivedByContract(@Param("contractId") String contractId);

    /**
     * 按月聚合回款金额
     *
     * @param initiationId 立项 ID
     * @return 月度聚合列表
     */
    List<Map<String, Object>> aggregateByMonth(@Param("initiationId") String initiationId);

    /**
     * 按客户聚合回款金额
     *
     * @return 客户聚合列表
     */
    List<Map<String, Object>> aggregateByCustomer();

    /**
     * 跨项目汇总已分配（确认）回款金额
     *
     * @return 已分配回款总额
     */
    BigDecimal sumAllocatedAmount();

    /**
     * P6 每日对账：跨项目汇总已分配金额（兼容 sumAmountAllocated）
     *
     * @return 已分配回款总额
     */
    BigDecimal sumAmountAllocated();

    /**
     * 批次18：跨项目按月汇总已确认回款金额（最近 N 个月）
     *
     * <p>用于 KPI 趋势的"已确认收入"序列。
     * 返回字段：month / amount / cnt
     */
    List<Map<String, Object>> aggregateByRecentMonth(@Param("limit") Integer limit);
}
