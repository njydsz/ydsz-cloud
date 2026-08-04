package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectPayment;
import com.njydsz.project.domain.repository.project.IProjectPaymentRepository;
import com.njydsz.project.server.service.ProjectPaymentService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目回款 Service 实现
 *
 * <p>对 {@link ProjectPaymentService} 接口的完整实现，是「项目管理 / 收入回款」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_payment} 项目回款表，
 * 对标大厂 PMIS / 财务系统的「客户回款 / 到账登记 / 回款核销 / 应收账款」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>回款登记</b>：客户付款后由财务登记回款（金额 / 到账日期 / 银行流水 / 付款方）</li>
 *   <li><b>回款核销</b>：回款自动核销关联合同 / 开票的应收账款余额</li>
 *   <li><b>应收账款</b>：维护开票未回款余额，支撑 AR 账龄分析</li>
 *   <li><b>客户对账</b>：按客户维度生成对账单，与客户月度对账</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>回款核销需与开票表、合同收款计划在同一事务</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>银行流水对账</b>：通过 {@code bankFlowNo} 关联银行流水，
 *       支撑银企直连自动对账</li>
 *   <li><b>回款类型</b>：支持 {@code CONTRACT} 合同回款 / {@code INSTALLMENT} 分期回款 /
 *       {@code ADVANCE} 预收款 / {@code OTHER} 其他</li>
 *   <li><b>回款核销策略</b>：默认按开票日期升序自动核销，特殊场景支持手工核销</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       回款记录是财务核算的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 财务登记客户回款
 * ProjectPayment payment = new ProjectPayment();
 * payment.setInitiationId("project_123");
 * payment.setContractId("contract_456");
 * payment.setInvoiceId("invoice_789");
 * payment.setPaymentType("CONTRACT");
 * payment.setAmount(new BigDecimal("555000"));
 * payment.setPaymentDate(LocalDate.now());
 * payment.setBankFlowNo("ICBC2026072812345678");
 * payment.setPayerName("某客户有限公司");
 * payment.setStatus("CONFIRMED");
 * projectPaymentService.save(payment);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectPaymentService 回款 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectPayment 回款实体
 * @see com.njydsz.project.server.service.impl.ProjectInvoiceServiceImpl 开票（核销源）
 * @see com.njydsz.project.server.service.impl.ProjectContractServiceImpl 合同（收款计划）
 */
@Service
@RequiredArgsConstructor
public class ProjectPaymentServiceImpl implements ProjectPaymentService {

    /** 回款仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectPaymentRepository repository;

    /**
     * 根据主键查询回款
     *
     * @param id 回款主键
     * @return 回款实体，不存在返回 null
     */
    @Override
    public ProjectPayment getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询回款
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code paymentType}、{@code status} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectPayment> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增回款
     *
     * <p>新增后应触发 {@code PaymentReceivedEvent} 领域事件，
     * 联动合同收款计划核销、AR 账龄更新、利润快照增量更新。
     *
     * @param payment 回款实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectPayment payment) {
        return repository.save(payment);
    }

    /**
     * 更新回款
     *
     * <p><b>注意：</b>已核销的回款（{@code status=RECONCILED}）<b>严禁</b>修改关键字段，
     * 错误应通过「红冲」流程纠正。
     *
     * @param payment 回款实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectPayment payment) {
        return repository.updateById(payment);
    }

    /**
     * 逻辑删除回款
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>回款记录是财务核算的法定依据，<b>严禁</b>物理删除。
     *
     * @param id 回款主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
