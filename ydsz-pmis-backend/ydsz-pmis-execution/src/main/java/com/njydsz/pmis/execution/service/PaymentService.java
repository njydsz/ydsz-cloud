package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.PaymentAllocationDTO;
import com.njydsz.pmis.execution.dto.PaymentCreateDTO;
import com.njydsz.pmis.execution.entity.PaymentDO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 回款服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface PaymentService {

    /**
     * 录入回款（PENDING 状态）
     */
    Long record(PaymentCreateDTO dto);

    /**
     * 确认到账 (PENDING → CONFIRMED)
     */
    void confirm(Long id, Long operatorId);

    /**
     * 取消 (PENDING/CONFIRMED → CANCELLED)
     */
    void cancel(Long id, Long operatorId, String reason);

    /**
     * 删除（仅 PENDING/CANCELLED 可删）
     */
    void delete(Long id);

    /**
     * 核销：把回款分配到发票
     */
    void allocate(PaymentAllocationDTO dto);

    /**
     * 自动核销：按客户维度，把已确认的回款按发票到期顺序自动分配
     */
    int autoAllocate(Long customerId, Long operatorId);

    /**
     * 现金流预测：基于回款历史 + 应收余额预测未来 N 个月回款
     */
    List<Map<String, Object>> forecastCashFlow(Long initiationId, int months);

    PaymentDO getById(Long id);

    Page<PaymentDO> page(int page, int size, String keyword, String status,
                         Long contractId, Long customerId, Long initiationId);

    /**
     * 合同累计回款金额
     */
    BigDecimal sumReceivedByContract(Long contractId);

    /**
     * 按月汇总回款
     */
    List<Map<String, Object>> aggregateByMonth(Long initiationId);

    /**
     * 按客户汇总
     */
    List<Map<String, Object>> aggregateByCustomer();
}
