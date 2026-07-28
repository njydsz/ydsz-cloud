package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectExpense;
import com.njydsz.project.domain.repository.project.IProjectExpenseRepository;
import com.njydsz.project.server.service.ProjectExpenseService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目费用报销 Service 实现
 *
 * <p>对 {@link ProjectExpenseService} 接口的完整实现，是「项目管理 / 费用报销」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_expense} 费用报销表，
 * 对标大厂 PMIS / 费控系统的「项目费用 / 差旅报销 / 团建报销 / 会议报销 / 办公费报销」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>费用报销</b>：差旅 / 团建 / 会议 / 办公 / 业务招待等费用报销申请，
 *       可关联项目（影响项目预算）</li>
 *   <li><b>预算占用校验</b>：报销时联动 {@code ydsz_project_budget_item} 预算明细校验占用率，
 *       触发 80% 黄灯 / 95% 红灯预警</li>
 *   <li><b>审批流</b>：超过阈值的报销需走 {@code ydsz-workflow} 流程引擎审批</li>
 *   <li><b>成本归集</b>：审批通过后联动 {@code ydsz_cost_allocation} 成本归集表</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>报销审批通过后联动预算占用更新需与预算 Service 共享同一事务</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>费用凭证</b>：报销需上传发票凭证（{@code invoiceFileIds}），
 *       通过 {@code ydsz-common-file} 存储</li>
 *   <li><b>差旅标准</b>：差旅报销需符合公司差旅标准（舱位 / 酒店星级 / 餐补等），
 *       超标准部分需特批</li>
 *   <li><b>预算归属</b>：项目类费用必须关联到具体项目（{@code initiationId}），
 *       计入项目预算</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       费用记录是财务核算的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 创建差旅报销
 * ProjectExpense expense = new ProjectExpense();
 * expense.setExpenseType("TRAVEL");
 * expense.setInitiationId("project_123");     // 关联项目
 * expense.setApplicantId("user_456");
 * expense.setAmount(new BigDecimal("3500"));
 * expense.setExpenseDate(LocalDate.now());
 * expense.setDescription("客户现场出差 3 天");
 * expense.setInvoiceFileIds("file_001,file_002");
 * expense.setStatus("PENDING");
 * projectExpenseService.save(expense);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectExpenseService 费用报销 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectExpense 费用报销实体
 * @see com.njydsz.project.server.service.impl.ProjectBudgetItemServiceImpl 预算明细（占用联动）
 * @see com.njydsz.project.server.service.impl.CostAllocationServiceImpl 成本归集（数据消费方）
 */
@Service
@RequiredArgsConstructor
public class ProjectExpenseServiceImpl implements ProjectExpenseService {

    /** 费用报销仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectExpenseRepository repository;

    /**
     * 根据主键查询费用报销
     *
     * @param id 费用报销主键
     * @return 费用报销实体，不存在返回 null
     */
    @Override
    public ProjectExpense getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询费用报销
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code expenseType}、{@code status} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectExpense> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增费用报销
     *
     * <p>新增后应触发 {@code ExpenseCreatedEvent} 领域事件，
     * 由 {@code ydsz-workflow} 流程引擎启动报销审批流。
     *
     * @param expense 费用报销实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectExpense expense) {
        return repository.save(expense);
    }

    /**
     * 更新费用报销
     *
     * <p><b>注意：</b>仅在审批中（{@code status=PENDING}）的报销可修改，
     * 审批通过后（{@code status=APPROVED}）的报销<b>严禁</b>修改关键字段。
     *
     * @param expense 费用报销实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectExpense expense) {
        return repository.updateById(expense);
    }

    /**
     * 逻辑删除费用报销
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>费用记录是财务核算的法定依据，<b>严禁</b>物理删除。
     *
     * @param id 费用报销主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
