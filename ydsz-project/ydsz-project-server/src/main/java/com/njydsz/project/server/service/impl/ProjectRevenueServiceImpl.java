package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectRevenue;
import com.njydsz.project.domain.repository.project.IProjectRevenueRepository;
import com.njydsz.project.server.service.ProjectRevenueService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目收入确认 Service 实现
 *
 * <p>对 {@link ProjectRevenueService} 接口的完整实现，是「项目管理 / 收入管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_revenue} 项目收入确认表，
 * 对标大厂 PMIS / 财务系统中的「收入确认 / 收入准则（IFRS 15 / CAS 14）/ 履约进度核算」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>收入确认</b>：按履约进度或里程碑确认收入，遵守新收入准则</li>
 *   <li><b>履约进度核算</b>：按产出法或投入法计算履约进度，
 *       支撑按完工进度确认收入</li>
 *   <li><b>收入类型</b>：支持 {@code TIMING} 时点确认 / {@code PERIODIC} 期间确认 /
 *       {@code MILESTONE} 里程碑确认</li>
 *   <li><b>开票触发</b>：收入确认后联动 {@code ydsz_project_invoice} 开票申请</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>收入确认与开票申请需在同一事务（数据一致性）</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>履约进度方法</b>：{@code progressMethod} 区分 {@code OUTPUT} 产出法 /
 *       {@code INPUT} 投入法</li>
 *   <li><b>收入准则</b>：默认遵循 CAS 14（中国会计准则），可切换 IFRS 15</li>
 *   <li><b>收入冲销</b>：错误确认需通过红冲流程，保留完整审计链</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       收入确认是税务申报的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 按里程碑确认收入
 * ProjectRevenue revenue = new ProjectRevenue();
 * revenue.setInitiationId("project_123");
 * revenue.setContractId("contract_456");
 * revenue.setRevenueType("MILESTONE");
 * revenue.setPeriod(LocalDate.of(2026, 7, 1));
 * revenue.setAmount(new BigDecimal("1000000"));
 * revenue.setProgressPct(new BigDecimal("0.20"));
 * revenue.setMilestone("M2-需求评审通过");
 * revenue.setStatus("RECOGNIZED");
 * projectRevenueService.save(revenue);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectRevenueService 收入确认 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectRevenue 收入确认实体
 * @see com.njydsz.project.server.service.impl.ProjectInvoiceServiceImpl 开票（联动目标）
 * @see com.njydsz.project.server.service.impl.ProjectProfitSnapshotServiceImpl 利润快照（收入消费方）
 */
@Service
@RequiredArgsConstructor
public class ProjectRevenueServiceImpl implements ProjectRevenueService {

    /** 收入确认仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectRevenueRepository repository;

    /**
     * 根据主键查询收入确认
     *
     * @param id 收入确认主键
     * @return 收入确认实体，不存在返回 null
     */
    @Override
    public ProjectRevenue getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询收入确认
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code revenueType}、{@code status} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectRevenue> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增收入确认
     *
     * <p>新增后应触发 {@code RevenueRecognizedEvent} 领域事件，
     * 联动开票申请和利润快照增量更新。
     *
     * @param revenue 收入确认实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectRevenue revenue) {
        return repository.save(revenue);
    }

    /**
     * 更新收入确认
     *
     * <p><b>注意：</b>已确认的收入（{@code status=RECOGNIZED}）的关键字段（金额 / 履约进度）
     * <b>严禁</b>修改，错误应通过「红冲」流程纠正。
     *
     * @param revenue 收入确认实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectRevenue revenue) {
        return repository.updateById(revenue);
    }

    /**
     * 逻辑删除收入确认
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>收入确认是税务申报的法定依据，<b>严禁</b>物理删除。
     *
     * @param id 收入确认主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
