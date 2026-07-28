package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.cost.CostAllocation;
import com.njydsz.project.domain.repository.cost.ICostAllocationRepository;
import com.njydsz.project.server.service.CostAllocationService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目成本归集 Service 实现
 *
 * <p>对 {@link CostAllocationService} 接口的完整实现，是「项目管理 / 财务成本归集」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_cost_allocation} 项目成本归集表，
 * 对标大厂 PMIS / ERP 系统的「项目成本归集 / 成本中心核算 / 期间成本结转」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>成本归集</b>：按 {@code period (YYYY-MM) × initiationId × costCategory} 维度归集
 *       项目当月发生的所有成本（人力 / 采购 / 费用 / 外包 / 分摊）</li>
 *   <li><b>多源汇总</b>：从 {@code ydsz_execution_time_entry}（工时成本）/
 *       {@code ydsz_cost_purchase}（采购成本）/{@code ydsz_project_expense}（费用）等
 *       多源数据按月聚合</li>
 *   <li><b>利润核算</b>：作为 {@code ydsz_project_profit_snapshot} 利润快照的输入数据之一</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>批量归集时建议按立项分批事务提交，避免大事务长锁</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>期间锁定</b>：已结账期间的归集数据<b>严禁</b>修改，错误应通过「红冲」流程纠正</li>
 *   <li><b>对账一致性</b>：归集数应与源表（{@code time_entry / cost_purchase / project_expense}）保持一致，
 *       差异通过 {@code ydsz_project_reconcile_daily} 日对账任务监控</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       成本归集是财务核算的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 定期任务按月归集项目成本
 * CostAllocation allocation = new CostAllocation();
 * allocation.setInitiationId("project_123");
 * allocation.setPeriod("2026-07");
 * allocation.setCostCategory("LABOR");
 * allocation.setAmount(new BigDecimal("500000"));
 * allocation.setSourceType("TIME_ENTRY");
 * costAllocationService.save(allocation);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see CostAllocationService 成本归集 Service 接口
 * @see com.njydsz.project.domain.entity.cost.CostAllocation 成本归集体体
 * @see com.njydsz.project.server.service.impl.ProjectProfitSnapshotServiceImpl 利润快照（数据消费方）
 * @see com.njydsz.project.server.service.impl.ProjectReconcileDailyServiceImpl 日对账（一致性校验）
 */
@Service
@RequiredArgsConstructor
public class CostAllocationServiceImpl implements CostAllocationService {

    /** 成本归集仓储（聚合 Mapper + 缓存 + 事件） */
    private final ICostAllocationRepository repository;

    /**
     * 根据主键查询成本归集
     *
     * @param id 成本归集主键
     * @return 成本归集实体，不存在返回 null
     */
    @Override
    public CostAllocation getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询成本归集
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code period}、
     * {@code initiationId}、{@code costCategory} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<CostAllocation> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增成本归集
     *
     * <p><b>典型调用方：</b>定时任务（每月 1 号凌晨滚动归集上月成本）。
     *
     * @param allocation 成本归集实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(CostAllocation allocation) {
        return repository.save(allocation);
    }

    /**
     * 更新成本归集
     *
     * <p><b>注意：</b>已结账期间的成本归集<b>严禁</b>修改，
     * 错误应通过「红冲」流程纠正。
     *
     * @param allocation 成本归集实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(CostAllocation allocation) {
        return repository.updateById(allocation);
    }

    /**
     * 逻辑删除成本归集
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>成本归集是财务核算的法定依据，<b>严禁</b>物理删除。
     *
     * @param id 成本归集主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
