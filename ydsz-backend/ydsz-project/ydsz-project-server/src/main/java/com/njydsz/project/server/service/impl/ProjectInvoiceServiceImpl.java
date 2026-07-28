package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectInvoice;
import com.njydsz.project.domain.repository.project.IProjectInvoiceRepository;
import com.njydsz.project.server.service.ProjectInvoiceService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目开票 Service 实现
 *
 * <p>对 {@link ProjectInvoiceService} 接口的完整实现，是「项目管理 / 收入开票」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_invoice} 项目开票表，
 * 对标大厂 PMIS / 财务系统的「开票申请 / 发票开具 / 销项发票」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>开票申请</b>：按合同 / 收入确认事件触发开票申请（含税金额 / 税率 / 发票类型）</li>
 *   <li><b>金税对接</b>：与金税系统对接开票，开票后回填发票号 / 发票代码 / 开票日期</li>
 *   <li><b>开票台账</b>：作为客户回款的对账依据，联动 {@code ydsz_project_payment} 回款表</li>
 *   <li><b>增值税管理</b>：维护税率 / 含税金额 / 不含税金额 / 税额，支撑增值税申报</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>金税开票回调同步需与相关服务在同一事务</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>金税接口</b>：与航天金税 / 百望等金税系统对接，
 *       开票状态由金税系统异步回调同步</li>
 *   <li><b>红字发票</b>：错票 / 退票需开具红字发票，保留完整审计链</li>
 *   <li><b>发票类型</b>：支持增值税专用发票 / 增值税普通发票 / 电子发票 / 增值税专用电子发票等</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       发票记录是税务合规的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 创建开票申请
 * ProjectInvoice invoice = new ProjectInvoice();
 * invoice.setInitiationId("project_123");
 * invoice.setContractId("contract_456");
 * invoice.setInvoiceType("VAT_SPECIAL");
 * invoice.setAmountWithTax(new BigDecimal("555000"));
 * invoice.setTaxRate(new BigDecimal("0.13"));
 * invoice.setAmountWithoutTax(new BigDecimal("491150"));
 * invoice.setTaxAmount(new BigDecimal("63850"));
 * invoice.setStatus("PENDING");
 * projectInvoiceService.save(invoice);
 *
 * // 2. 金税开票回调更新
 * // 由金税系统 webhook 回调触发
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectInvoiceService 开票 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectInvoice 开票实体
 * @see com.njydsz.project.server.service.impl.ProjectPaymentServiceImpl 回款（开票对账）
 * @see com.njydsz.project.server.service.impl.ProjectRevenueServiceImpl 收入确认（开票触发源）
 */
@Service
@RequiredArgsConstructor
public class ProjectInvoiceServiceImpl implements ProjectInvoiceService {

    /** 开票仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectInvoiceRepository repository;

    /**
     * 根据主键查询开票
     *
     * @param id 开票主键
     * @return 开票实体，不存在返回 null
     */
    @Override
    public ProjectInvoice getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询开票
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code invoiceType}、{@code status} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectInvoice> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增开票
     *
     * <p>新增后触发金税开票，状态由金税异步回调更新。
     *
     * @param invoice 开票实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectInvoice invoice) {
        return repository.save(invoice);
    }

    /**
     * 更新开票
     *
     * <p><b>注意：</b>已开具的发票（{@code status=ISSUED}）的关键字段（金额 / 税率）
     * <b>严禁</b>修改，错误应通过「红冲」流程纠正。
     *
     * @param invoice 开票实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectInvoice invoice) {
        return repository.updateById(invoice);
    }

    /**
     * 逻辑删除开票
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>发票记录是税务合规的法定依据，<b>严禁</b>物理删除。
     *
     * @param id 开票主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
