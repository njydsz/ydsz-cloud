package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectExpense;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 项目费用 Service
 *
 * <p>管理项目费用（{@code ydsz_project_expense}）的录入、审批、分摊。
 * 费用指项目执行过程中除工时外的所有支出（差旅/采购/外协/招待等）,
 * 是项目成本的重要组成部分,直接进入利润计算。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>费用类型</b>：差旅 / 采购 / 外协 / 招待 / 其他</li>
 *   <li><b>审批流</b>：超过阈值的费用走 {@code workflow} 审批</li>
 *   <li><b>成本分摊</b>：跨项目费用按比例分摊</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectExpense 费用实体
 * @see CostPurchaseService 采购费用 Service(采购订单产生的费用)
 * @see CostAllocationService 成本分摊 Service(跨项目分摊)
 */
public interface ProjectExpenseService {
    ProjectExpense getById(String id);
    IPage<ProjectExpense> page(int pageNum, int pageSize);
    boolean save(ProjectExpense entity);
    boolean updateById(ProjectExpense entity);
    boolean removeById(String id);
}
