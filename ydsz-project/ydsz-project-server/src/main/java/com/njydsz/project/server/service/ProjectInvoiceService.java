package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectInvoice;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 项目发票 Service
 *
 * <p>管理项目发票（{@code ydsz_project_invoice}）的开具、寄送、回执、核销。
 * 发票是合同收入的合法凭据,开票后客户按票面金额回款,回款核销发票完成收入闭环。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>开票类型</b>：增值税专票 / 增值税普票 / 电子发票</li>
 *   <li><b>回执核销</b>：客户回款后自动核销</li>
 * </ul>
 *
 * <p><b>税控集成：</b>通过 {@code ydsz.finance.invoice.tax-control-api-url} 配置对接税控系统。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectInvoice 发票实体
 * @see ProjectPaymentService 回款 Service(回款核销发票)
 * @see ProjectRevenueService 收入 Service(开票触发收入确认)
 */
public interface ProjectInvoiceService {
    ProjectInvoice getById(String id);
    IPage<ProjectInvoice> page(int pageNum, int pageSize);
    boolean save(ProjectInvoice entity);
    boolean updateById(ProjectInvoice entity);
    boolean removeById(String id);
}
