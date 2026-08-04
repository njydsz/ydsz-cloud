package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot;
import com.njydsz.project.domain.repository.billable.IBillableUtilizationSnapshotRepository;
import com.njydsz.project.server.service.BillableUtilizationSnapshotService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计费率 / 资源利用率快照 Service 实现
 *
 * <p>对 {@link BillableUtilizationSnapshotService} 接口的完整实现，是「项目管理 / 资源管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_billable_utilization_snapshot} 计费率快照表，
 * 对标大厂 PMIS / 资源管理系统中的「人员计费率 / 资源利用率 / 资源台账」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>周期性快照</b>：按 {@code period (YYYY-MM) × employeeId} 维度滚动生成，
 *       记录员工当月的工时构成（可计费 / 加班 / 请假 / 培训 / 闲置）</li>
 *   <li><b>资源利用率分析</b>：为资源管理「计费率仪表盘」「资源热力图」提供数据源，
 *       支撑 PM / 资源经理的资源分配决策</li>
 *   <li><b>成本中心核算</b>：联动 {@code ydsz_rate_internal} 内部费率表计算人力成本中心费用</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>批量生成快照时建议按员工分批事务提交，避免大事务长锁</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>数据来源</b>：由 {@code ydsz-job-cronjob} 定时任务（每月 1 号凌晨）从
 *       {@code ydsz_execution_time_entry} 聚合生成</li>
 *   <li><b>计费率公式</b>：{@code billableUtilization = billable_hours / total_hours}，
 *       行业基准 70%-85%</li>
 *   <li><b>快照不可变</b>：快照数据生成后<b>不应</b>频繁 update，作为历史台账保留</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       历史快照需保留供审计和趋势分析</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 定期任务生成当月计费率快照
 * BillableUtilizationSnapshot snapshot = new BillableUtilizationSnapshot();
 * snapshot.setPeriod("2026-07");
 * snapshot.setEmployeeId("user_123");
 * snapshot.setEmployeeName("张三");
 * snapshot.setLevelCode("L8");
 * snapshot.setDepartment("研发中心 / 实施组");
 * snapshot.setTotalHours(new BigDecimal("176"));
 * snapshot.setBillableHours(new BigDecimal("150"));
 * snapshot.setUtilization(new BigDecimal("0.8523"));
 * billableUtilizationSnapshotService.save(snapshot);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see BillableUtilizationSnapshotService 计费率快照 Service 接口
 * @see com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot 计费率快照实体
 * @see com.njydsz.project.server.service.impl.RateInternalServiceImpl 内部费率 Service（成本联动）
 * @see com.njydsz.project.server.service.impl.ExecutionTimeEntryServiceImpl 工时录入（数据源）
 */
@Service
@RequiredArgsConstructor
public class BillableUtilizationSnapshotServiceImpl implements BillableUtilizationSnapshotService {

    /** 计费率快照仓储（聚合 Mapper + 缓存 + 事件） */
    private final IBillableUtilizationSnapshotRepository repository;

    /**
     * 根据主键查询计费率快照
     *
     * @param id 计费率快照主键
     * @return 计费率快照实体，不存在返回 null
     */
    @Override
    public BillableUtilizationSnapshot getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询计费率快照
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code period}、
     * {@code employeeId}、{@code department} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<BillableUtilizationSnapshot> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增计费率快照
     *
     * <p><b>典型调用方：</b>定时任务（每月 1 号凌晨滚动生成上月快照）。
     *
     * @param snapshot 计费率快照实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(BillableUtilizationSnapshot snapshot) {
        return repository.save(snapshot);
    }

    /**
     * 更新计费率快照
     *
     * <p><b>注意：</b>快照数据通常<b>不建议</b>直接 update（保留历史快照的不可变性），
     * 推荐通过 {@code save} 走 UPSERT 语义覆盖。
     *
     * @param snapshot 计费率快照实体（需携带 ID 和当前 version）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(BillableUtilizationSnapshot snapshot) {
        return repository.updateById(snapshot);
    }

    /**
     * 逻辑删除计费率快照
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除，便于审计和趋势分析。
     *
     * @param id 计费率快照主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
