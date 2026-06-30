package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.InvoiceApprovalDTO;
import com.njydsz.pmis.execution.dto.InvoiceCreateDTO;
import com.njydsz.pmis.execution.entity.InvoiceDO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 发票服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface InvoiceService {

    /**
     * 创建发票申请（草稿）
     */
    Long create(InvoiceCreateDTO dto);

    /**
     * 提交审批 (DRAFT → SUBMITTED)
     */
    void submit(Long id, Long operatorId);

    /**
     * 审批通过 (SUBMITTED → APPROVED)
     */
    void approve(Long id, InvoiceApprovalDTO dto);

    /**
     * 审批驳回 (SUBMITTED → REJECTED)
     */
    void reject(Long id, InvoiceApprovalDTO dto);

    /**
     * 财务开具 (APPROVED → ISSUED)
     */
    void issue(Long id, InvoiceApprovalDTO dto);

    /**
     * 红冲 (ISSUED → RED_REVERSED)
     */
    void redReverse(Long id, Long operatorId, String comment);

    /**
     * 取消 (DRAFT/APPROVED → CANCELLED)
     */
    void cancel(Long id, Long operatorId, String comment);

    /**
     * 删除（仅 DRAFT 状态可删）
     */
    void delete(Long id);

    InvoiceDO getById(Long id);

    Page<InvoiceDO> page(int page, int size, String keyword, String status,
                         Long contractId, Long initiationId, Long customerId,
                         String invoiceType);

    List<InvoiceDO> listByContract(Long contractId);

    List<InvoiceDO> listByInitiation(Long initiationId);

    /**
     * 合同累计开票金额（仅 NORMAL+APPROVED/ISSUED）
     */
    BigDecimal sumInvoicedByContract(Long contractId);

    /**
     * 开票台账（按状态分组）
     */
    List<Map<String, Object>> aggregateByStatus(Long contractId);

    /**
     * 按月汇总开票
     */
    List<Map<String, Object>> sumByMonth(Long initiationId);
}
