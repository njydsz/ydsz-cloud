package com.njydsz.project.server.service.finance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.dto.InvoiceApprovalDTO;
import com.njydsz.project.domain.dto.InvoiceCreateDTO;
import com.njydsz.project.domain.entity.InvoiceDO;

/**
 * 发票服务
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface InvoiceService {

    /**
     * 创建发票申请（草稿）
     */
    String create(InvoiceCreateDTO dto);

    /**
     * 提交审批 (DRAFT → SUBMITTED)
     */
    void submit(String id, String operatorId);

    /**
     * 审批通过 (SUBMITTED → APPROVED)
     */
    void approve(String id, InvoiceApprovalDTO dto);

    /**
     * 审批驳回 (SUBMITTED → REJECTED)
     */
    void reject(String id, InvoiceApprovalDTO dto);

    /**
     * 财务开具 (APPROVED → ISSUED)
     */
    void issue(String id, InvoiceApprovalDTO dto);

    /**
     * 红冲 (ISSUED → RED_REVERSED)
     */
    void redReverse(String id, String operatorId, String comment);

    /**
     * 取消 (DRAFT/APPROVED → CANCELLED)
     */
    void cancel(String id, String operatorId, String comment);

    /**
     * 删除（仅 DRAFT 状态可删）
     */
    void delete(String id);

    /**
     * 根据ID查询发票
     *
     * @param id 发票ID
     * @return 发票实体
     */
    InvoiceDO getById(String id);

    /**
     * 分页查询发票
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param contractId   合同ID
     * @param initiationId 项目立项ID
     * @param customerId   客户ID
     * @param invoiceType  发票类型
     * @return 分页结果
     */
    Page<InvoiceDO> page(int page, int size, String keyword, String status,
                         String contractId, String initiationId, String customerId,
                         String invoiceType);

    /**
     * 查询合同下所有发票
     *
     * @param contractId 合同ID
     * @return 发票列表
     */
    List<InvoiceDO> listByContract(String contractId);

    /**
     * 查询项目下所有发票
     *
     * @param initiationId 项目立项ID
     * @return 发票列表
     */
    List<InvoiceDO> listByInitiation(String initiationId);

    /**
     * 合同累计开票金额（仅 NORMAL+APPROVED/ISSUED）
     */
    BigDecimal sumInvoicedByContract(String contractId);

    /**
     * 开票台账（按状态分组）
     */
    List<Map<String, Object>> aggregateByStatus(String contractId);

    /**
     * 按月汇总开票
     */
    List<Map<String, Object>> sumByMonth(String initiationId);
}
