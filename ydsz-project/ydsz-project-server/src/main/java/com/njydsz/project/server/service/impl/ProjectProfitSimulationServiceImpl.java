package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectProfitSimulation;
import com.njydsz.project.domain.repository.project.IProjectProfitSimulationRepository;
import com.njydsz.project.server.service.ProjectProfitSimulationService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目利润模拟 Service 实现
 *
 * <p>对 {@link ProjectProfitSimulationService} 接口的完整实现，是「项目管理 / 财务经营分析 / 利润预测」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_profit_simulation} 利润模拟推演表，
 * 对标大厂 PMIS / 经营分析系统中的「What-If 利润模拟 / 利润敏感性分析 / 项目定价测算」能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>What-If 模拟</b>：基于不同成本 / 收入假设，模拟项目最终利润</li>
 *   <li><b>敏感性分析</b>：分析关键变量（人力成本 / 采购成本 / 验收比例）对利润的敏感度</li>
 *   <li><b>定价测算</b>：基于目标利润率反推项目报价</li>
 *   <li><b>情景对比</b>：支持保存多个模拟方案，横向对比差异</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>模拟方案保存为不可变快照，不与原项目数据共享事务</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>快照隔离</b>：模拟时复制项目当时的预算 / 合同 / 成本快照作为基线，
 *       后续原项目数据变更不影响历史模拟</li>
 *   <li><b>变量维度</b>：支持调整 LABOR / PURCHASE / EXPENSE / OUTSOURCE 四类成本变量</li>
 *   <li><b>假设追溯</b>：每条模拟记录保存完整假设参数（{@code assumptionsJson}），
 *       支撑复盘</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       模拟方案是经营决策的参考依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 销售在报价前做利润模拟
 * ProjectProfitSimulation sim = new ProjectProfitSimulation();
 * sim.setInitiationId("project_123");
 * sim.setScenarioName("报价方案A-标准配置");
 * sim.setAssumptionsJson("{\"laborRate\":1500,\"laborDays\":2000,...}");
 * sim.setEstimatedRevenue(new BigDecimal("5000000"));
 * sim.setEstimatedCost(new BigDecimal("3800000"));
 * sim.setEstimatedProfit(new BigDecimal("1200000"));
 * sim.setProfitRate(new BigDecimal("0.24"));
 * sim.setCreatedBy("user_sales_001");
 * projectProfitSimulationService.save(sim);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectProfitSimulationService 利润模拟 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectProfitSimulation 利润模拟实体
 * @see com.njydsz.project.server.service.impl.ProjectProfitSnapshotServiceImpl 利润快照（实际利润基线）
 * @see com.njydsz.project.server.service.impl.ProjectBudgetItemServiceImpl 预算明细（成本基线）
 */
@Service
@RequiredArgsConstructor
public class ProjectProfitSimulationServiceImpl implements ProjectProfitSimulationService {

    /** 利润模拟仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectProfitSimulationRepository repository;

    /**
     * 根据主键查询利润模拟
     *
     * @param id 利润模拟主键
     * @return 利润模拟实体，不存在返回 null
     */
    @Override
    public ProjectProfitSimulation getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询利润模拟
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code scenarioName} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectProfitSimulation> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增利润模拟
     *
     * <p>典型调用方：销售报价工作台 / 项目经理经营分析页面。
     *
     * @param simulation 利润模拟实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectProfitSimulation simulation) {
        return repository.save(simulation);
    }

    /**
     * 更新利润模拟
     *
     * <p><b>注意：</b>已固化的模拟方案（{@code status=FROZEN}）<b>严禁</b>修改，
     * 调整应创建新方案，保留历史。
     *
     * @param simulation 利润模拟实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectProfitSimulation simulation) {
        return repository.updateById(simulation);
    }

    /**
     * 逻辑删除利润模拟
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>模拟方案是经营决策的参考依据，<b>严禁</b>物理删除。
     *
     * @param id 利润模拟主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
