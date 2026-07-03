package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.PaymentAllocationDTO;
import com.njydsz.pmis.project.dto.PaymentCreateDTO;
import com.njydsz.pmis.project.entity.PaymentDO;

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

    /**
     * 根据ID查询回款记录
     *
     * @param id 回款ID
     * @return 回款实体
     */
    PaymentDO getById(Long id);

    /**
     * 分页查询回款记录
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param contractId   合同ID
     * @param customerId   客户ID
     * @param initiationId 项目立项ID
     * @return 分页结果
     */
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
