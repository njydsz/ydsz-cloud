package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectProfitSnapshot;
import com.njydsz.project.domain.repository.project.IProjectProfitSnapshotRepository;
import com.njydsz.project.server.service.ProjectProfitSnapshotService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目利润快照 Service 实现
 *
 * <p>对 {@link ProjectProfitSnapshotService} 接口的完整实现，是「项目管理 / 财务经营分析」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_profit_snapshot} 利润快照表，对标大厂 PMIS / 经营分析系统中的「项目利润台账 / 项目经营月报」能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>周期性快照</b>：按 {@code initiation_id × period (YYYY-MM)} 唯一约束滚动生成，
 *       记录立项的月度经营指标（合同 / 收入 / 成本 / 毛利 / 毛利率 / 进度 / 工时）</li>
 *   <li><b>多维财务指标</b>：维护 5 类成本（人力 / 采购 / 费用 / 外包 / 分摊）和 4 类收入指标
 *       （合同 / 已确认 / 已开票 / 已回款），构成完整的「利润金字塔」</li>
 *   <li><b>经营分析数据源</b>：与 {@code ydsz_finance_profit_*} 报表系列、{@code Grafana} 仪表盘联动，
 *       支撑项目经理「项目经营月报」和管理层「公司经营看板」</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交</li>
 *   <li>批量生成快照时建议按立项分批事务提交，避免大事务长锁</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>快照不可变</b>：快照数据生成后<b>不应</b>频繁 update，作为历史台账保留；
 *       当月新数据通过重新生成覆盖（{@code UPSERT} 语义）</li>
 *   <li><b>唯一约束</b>：{@code (initiation_id, period, deleted)} 唯一，由 DB 端
 *       {@code uk_pps_init_period} 保证，避免重复生成</li>
 *   <li><b>数据完整性</b>：依赖 DB 端 {@code CHECK} 约束（{@code ck_pps_margin_range} 等）
 *       保证 {@code gross_margin ∈ [0,1]}、{@code progress_pct ∈ [0,100]} 等业务规则</li>
 *   <li><b>链路追踪</b>：{@code provider_trace_id} 字段记录快照生成的分布式链路 ID，
 *       便于问题排查</li>
 *   <li><b>软删除</b>：{@code ydsz_project_profit_snapshot} 表采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       历史快照需保留供审计回溯</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 定期任务生成当月快照
 * ProjectProfitSnapshot snapshot = new ProjectProfitSnapshot();
 * snapshot.setInitiationId("project_123");
 * snapshot.setPeriod("2026-07");
 * snapshot.setContractAmount(new BigDecimal("5000000"));
 * snapshot.setRecognizedRevenue(new BigDecimal("1000000"));
 * snapshot.setLaborCost(new BigDecimal("400000"));
 * snapshot.setPurchaseCost(new BigDecimal("50000"));
 * snapshot.setTotalCost(new BigDecimal("500000"));
 * snapshot.setGrossProfit(new BigDecimal("500000"));
 * snapshot.setGrossMargin(new BigDecimal("0.5"));
 * snapshot.setProgressPct(new BigDecimal("20"));
 * snapshot.setSnapshotAt(LocalDateTime.now());
 * projectProfitSnapshotService.save(snapshot);
 *
 * // 2. 查询某立项历史快照
 * // 走 wrapper.eq(ProjectProfitSnapshot::getInitiationId, "project_123")
 * //      .orderByDesc(ProjectProfitSnapshot::getPeriod)
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectProfitSnapshotService 利润快照 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectProfitSnapshot 利润快照实体
 * @see com.njydsz.project.server.service.impl.ProjectProfitSimulationServiceImpl 利润模拟（实时计算）
 * @see com.njydsz.project.server.service.impl.ProjectReconcileDailyServiceImpl 日对账（快照数据源之一）
 */
@Service
@RequiredArgsConstructor
public class ProjectProfitSnapshotServiceImpl implements ProjectProfitSnapshotService {

    /** 利润快照仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectProfitSnapshotRepository repository;

    /**
     * 根据主键查询利润快照
     *
     * <p>适用场景：经营分析报表 / Grafana 仪表盘详情查看。
     *
     * @param id 利润快照主键
     * @return 利润快照实体，不存在返回 null
     */
    @Override
    public ProjectProfitSnapshot getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询利润快照
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code period} 范围等），底层 MyBatis-Plus 自动追加 {@code deleted=0} 逻辑删除条件。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectProfitSnapshot> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增利润快照
     *
     * <p><b>典型调用方：</b>定时任务（每月 1 号凌晨滚动生成上月快照）。
     *
     * <p>依赖 DB 端 {@code uk_pps_init_period} 唯一约束保证 {@code (initiation_id, period)} 唯一，
     * 重复提交会抛 {@code DuplicateKeyException}，调用方需捕获并降级为 update。
     *
     * @param snapshot 利润快照实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectProfitSnapshot snapshot) {
        return repository.save(snapshot);
    }

    /**
     * 更新利润快照
     *
     * <p>更新时 MyBatis-Plus 会自动校验 {@code version} 乐观锁，并发更新时旧版本提交会被拒绝。
     *
     * <p><b>注意：</b>快照数据通常<b>不建议</b>直接 update（保留历史快照的不可变性），
     * 推荐通过 {@code save} 走 UPSERT 语义覆盖。
     *
     * @param snapshot 利润快照实体（需携带 ID 和当前 version）
     * @return true=更新成功，false=乐观锁冲突或记录不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectProfitSnapshot snapshot) {
        return repository.updateById(snapshot);
    }

    /**
     * 逻辑删除利润快照
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除，便于审计回溯。
     *
     * <p><b>注意：</b>利润快照是历史台账，原则上<b>严禁</b>删除，仅在数据录入错误等极端情况下使用。
     * 推荐通过「作废快照 + 新增正确快照」的方式纠正。
     *
     * @param id 利润快照主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
