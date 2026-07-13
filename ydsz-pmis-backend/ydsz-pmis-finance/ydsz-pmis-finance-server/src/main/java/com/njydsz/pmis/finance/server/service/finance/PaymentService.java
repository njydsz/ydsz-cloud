package com.njydsz.pmis.finance.server.service.finance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.finance.domain.dto.PaymentAllocationDTO;
import com.njydsz.pmis.finance.domain.dto.PaymentCreateDTO;
import com.njydsz.pmis.finance.domain.entity.PaymentDO;

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
    String record(PaymentCreateDTO dto);

    /**
     * 确认到账 (PENDING → CONFIRMED)
     */
    void confirm(String id, String operatorId);

    /**
     * 取消 (PENDING/CONFIRMED → CANCELLED)
     */
    void cancel(String id, String operatorId, String reason);

    /**
     * 删除（仅 PENDING/CANCELLED 可删）
     */
    void delete(String id);

    /**
     * 核销：把回款分配到发票
     */
    void allocate(PaymentAllocationDTO dto);

    /**
     * 自动核销：按客户维度，把已确认的回款按发票到期顺序自动分配
     */
    int autoAllocate(String customerId, String operatorId);

    /**
     * 现金流预测：基于回款历史 + 应收余额预测未来 N 个月回款
     */
    List<Map<String, Object>> forecastCashFlow(String initiationId, int months);

    /**
     * 根据ID查询回款记录
     *
     * @param id 回款ID
     * @return 回款实体
     */
    PaymentDO getById(String id);

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
                         String contractId, String customerId, String initiationId);

    /**
     * 合同累计回款金额
     */
    BigDecimal sumReceivedByContract(String contractId);

    /**
     * 按月汇总回款
     */
    List<Map<String, Object>> aggregateByMonth(String initiationId);

    /**
     * 按客户汇总
     */
    List<Map<String, Object>> aggregateByCustomer();
}
