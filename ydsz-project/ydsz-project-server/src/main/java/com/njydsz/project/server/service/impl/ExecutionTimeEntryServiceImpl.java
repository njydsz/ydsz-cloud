package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.execution.ExecutionTimeEntry;
import com.njydsz.project.domain.repository.execution.IExecutionTimeEntryRepository;
import com.njydsz.project.server.service.ExecutionTimeEntryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目工时录入 Service 实现
 *
 * <p>对 {@link ExecutionTimeEntryService} 接口的完整实现，是「项目管理 / 工时管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_execution_time_entry} 工时录入表，
 * 对标大厂 PMIS / 工时管理系统的「项目工时 / 工时填报 / 工时审批 / 工时成本核算」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>工时填报</b>：员工每日填报项目工时（日清日结），含可计费 / 不可计费工时</li>
 *   <li><b>工时审批</b>：工时由 PM 审批后生效，审批通过后联动 {@code ydsz_cost_allocation} 成本归集</li>
 *   <li><b>工时成本核算</b>：基于 {@code ydsz_rate_internal} 内部费率计算人力成本，
 *       自动归集到项目成本</li>
 *   <li><b>工时分析</b>：为「项目工时分布」「员工计费率」「部门资源利用」等分析报表提供数据源</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>批量工时导入时建议按员工分批事务提交，避免大事务长锁</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>日清日结</b>：工时必须当日填报，跨日补录需 PM 特批</li>
 *   <li><b>工时审批</b>：{@code status} 字段管理工时状态（{@code DRAFT} 草稿 /
 *       {@code SUBMITTED} 已提交 / {@code APPROVED} 已审批 / {@code REJECTED} 已驳回）</li>
 *   <li><b>联动成本</b>：审批通过后通过 {@code TimeEntryApprovedEvent} 异步触发成本归集</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       工时是成本核算的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 员工填报工时
 * ExecutionTimeEntry entry = new ExecutionTimeEntry();
 * entry.setInitiationId("project_123");
 * entry.setEmployeeId("user_123");
 * entry.setWorkDate(LocalDate.now());
 * entry.setHours(new BigDecimal("8"));
 * entry.setBillable(true);
 * entry.setDescription("需求评审 + 详细设计");
 * entry.setStatus("SUBMITTED");
 * executionTimeEntryService.save(entry);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ExecutionTimeEntryService 工时录入 Service 接口
 * @see com.njydsz.project.domain.entity.execution.ExecutionTimeEntry 工时录入实体
 * @see com.njydsz.project.server.service.impl.CostAllocationServiceImpl 成本归集（工时审批后联动）
 * @see com.njydsz.project.server.service.impl.RateInternalServiceImpl 内部费率（成本核算基础）
 */
@Service
@RequiredArgsConstructor
public class ExecutionTimeEntryServiceImpl implements ExecutionTimeEntryService {

    /** 工时录入仓储（聚合 Mapper + 缓存 + 事件） */
    private final IExecutionTimeEntryRepository repository;

    /**
     * 根据主键查询工时
     *
     * @param id 工时主键
     * @return 工时实体，不存在返回 null
     */
    @Override
    public ExecutionTimeEntry getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询工时
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code employeeId}、{@code workDate} 范围等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ExecutionTimeEntry> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增工时
     *
     * <p>提交后触发 {@code TimeEntrySubmittedEvent} 领域事件，
     * 由 PM 工作台异步处理审批通知。
     *
     * @param entry 工时实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ExecutionTimeEntry entry) {
        return repository.save(entry);
    }

    /**
     * 更新工时
     *
     * <p>典型场景：PM 审批、补充说明、调整工时。
     *
     * @param entry 工时实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ExecutionTimeEntry entry) {
        return repository.updateById(entry);
    }

    /**
     * 逻辑删除工时
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>工时是成本核算的法定依据，<b>严禁</b>物理删除，
     * 错误应通过「红冲」流程纠正。
     *
     * @param id 工时主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
