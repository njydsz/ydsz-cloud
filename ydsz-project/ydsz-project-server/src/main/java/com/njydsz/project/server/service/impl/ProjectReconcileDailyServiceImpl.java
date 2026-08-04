package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectReconcileDaily;
import com.njydsz.project.domain.repository.project.IProjectReconcileDailyRepository;
import com.njydsz.project.server.service.ProjectReconcileDailyService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目日对账 Service 实现
 *
 * <p>对 {@link ProjectReconcileDailyService} 接口的完整实现，是「项目管理 / 财务对账」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_reconcile_daily} 日对账表，
 * 对标大厂 PMIS / 财务系统中的「日清日结 / 银行流水对账 / 业务单据对账」能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>日清日结</b>：每日凌晨自动执行对账任务，核对前一天业务单据（合同 / 发票 / 回款）</li>
 *   <li><b>对账差异</b>：记录业务系统与财务系统的差异（{@code diffAmount}），
 *       差异超阈值时触发告警</li>
 *   <li><b>对账闭环</b>：对账完成后生成对账报告，由财务确认</li>
 *   <li><b>审计追溯</b>：日对账记录是合规审计和监管检查的依据</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>对账差异调整需与原业务单据（合同 / 发票 / 回款）共享同一事务</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>对账维度</b>：支持按合同 / 发票 / 回款三个维度对账</li>
 *   <li><b>差异分类</b>：{@code diffType} 区分差异类型（{@code AMOUNT} 金额 /
 *       {@code DATE} 日期 / {@code MISSING} 缺失）</li>
 *   <li><b>定时触发</b>：由 {@code ydsz-cronjob} 每日凌晨 02:00 触发对账</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       对账记录是财务合规的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 定时任务生成日对账记录
 * ProjectReconcileDaily daily = new ProjectReconcileDaily();
 * daily.setReconcileDate(LocalDate.now().minusDays(1));
 * daily.setReconcileType("PAYMENT");
 * daily.setTotalCount(35);
 * daily.setMatchCount(33);
 * daily.setDiffCount(2);
 * daily.setDiffAmount(new BigDecimal("500.00"));
 * daily.setStatus("PENDING_REVIEW");
 * projectReconcileDailyService.save(daily);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectReconcileDailyService 日对账 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectReconcileDaily 日对账实体
 * @see com.njydsz.project.server.service.impl.ProjectPaymentServiceImpl 回款（对账源）
 * @see com.njydsz.project.server.service.impl.ProjectInvoiceServiceImpl 开票（对账源）
 */
@Service
@RequiredArgsConstructor
public class ProjectReconcileDailyServiceImpl implements ProjectReconcileDailyService {

    /** 日对账仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectReconcileDailyRepository repository;

    /**
     * 根据主键查询日对账
     *
     * @param id 日对账主键
     * @return 日对账实体，不存在返回 null
     */
    @Override
    public ProjectReconcileDaily getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询日对账
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code reconcileDate}、
     * {@code reconcileType}、{@code status} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectReconcileDaily> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增日对账
     *
     * <p><b>典型调用方：</b>定时任务（每日凌晨自动生成）。
     *
     * @param reconcile 日对账实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectReconcileDaily reconcile) {
        return repository.save(reconcile);
    }

    /**
     * 更新日对账
     *
     * <p>典型场景：差异调整、补充对账说明、确认对账结果。
     *
     * @param reconcile 日对账实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectReconcileDaily reconcile) {
        return repository.updateById(reconcile);
    }

    /**
     * 逻辑删除日对账
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>对账记录是财务合规的法定依据，<b>严禁</b>物理删除。
     *
     * @param id 日对账主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
