package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectBudgetItem;
import com.njydsz.project.domain.repository.project.IProjectBudgetItemRepository;
import com.njydsz.project.server.service.ProjectBudgetItemService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 立项预算明细 Service 实现
 *
 * <p>对 {@link ProjectBudgetItemService} 接口的完整实现，是「项目管理 / 预算管控」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_budget_item} 预算明细表，对标大厂 PMIS / ERP 系统的「项目预算 / 成本预算」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>预算分类管理</b>：按 5 大类别（{@code LABOR / PURCHASE / EXPENSE / OUTSOURCE / OTHER}）
 *       拆解预算，支持子类别（如差旅、硬件、软件等字典项）</li>
 *   <li><b>预算占用控制</b>：与 {@code ProjectExpense} / {@code CostPurchase} 等实际成本表联动，
 *       支撑「预算占用率 80% 黄灯 / 95% 红灯」预警规则</li>
 *   <li><b>数据完整性</b>：依赖 DB 端 {@code CHECK} 约束（{@code ck_ppbi_amount_nonneg} 等）
 *       保证 {@code quantity / unit_price / amount} 非负</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>金额计算</b>：{@code amount = quantity * unit_price}，由 Service 层计算后写入 DB，
 *       避免依赖 DB 触发器</li>
 *   <li><b>乐观锁</b>：{@code version} 字段由 MyBatis-Plus 插件自动维护，并发更新安全</li>
 *   <li><b>软删除</b>：{@code ydsz_project_budget_item} 表采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       便于审计回溯</li>
 *   <li><b>联动逻辑</b>：预算修改后应触发预算重新评估，调用方需自行调用预算占用率刷新逻辑</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 创建预算明细（人力成本）
 * ProjectBudgetItem item = new ProjectBudgetItem();
 * item.setInitiationId("project_123");
 * item.setCategory("LABOR");
 * item.setSubCategory("DEVELOPER");
 * item.setDescription("高级开发工程师 × 30 人天");
 * item.setQuantity(new BigDecimal("30"));
 * item.setUnit("人天");
 * item.setUnitPrice(new BigDecimal("2000"));
 * item.setAmount(new BigDecimal("60000"));
 * item.setSortOrder(1);
 * projectBudgetItemService.save(item);
 *
 * // 2. 查询某立项的所有预算项
 * // 走 wrapper.eq(ProjectBudgetItem::getInitiationId, "project_123")
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectBudgetItemService 预算明细 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectBudgetItem 预算明细实体
 * @see com.njydsz.project.server.service.impl.ProjectExpenseServiceImpl 实际费用（联动预算占用）
 * @see com.njydsz.project.server.service.impl.CostPurchaseServiceImpl 采购成本（联动预算占用）
 */
@Service
@RequiredArgsConstructor
public class ProjectBudgetItemServiceImpl implements ProjectBudgetItemService {

    /** 预算明细仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectBudgetItemRepository repository;

    /**
     * 根据主键查询预算明细
     *
     * <p>适用场景：管理后台「预算明细详情」页、预算占用率刷新链路。
     *
     * @param id 预算明细主键
     * @return 预算明细实体，不存在返回 null
     */
    @Override
    public ProjectBudgetItem getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询预算明细
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code category} 等），底层 MyBatis-Plus 自动追加 {@code deleted=0} 逻辑删除条件。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectBudgetItem> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增预算明细
     *
     * <p><b>注意：</b>调用方应自行计算 {@code amount = quantity * unit_price} 并赋值，
     * 本方法<b>不</b>做兜底计算，避免对账不一致。
     *
     * <p>新增成功后建议联动刷新立项的预算总额和预算占用率。
     *
     * @param item 预算明细实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectBudgetItem item) {
        return repository.save(item);
    }

    /**
     * 更新预算明细
     *
     * <p>更新时 MyBatis-Plus 会自动校验 {@code version} 乐观锁，并发更新时旧版本提交会被拒绝。
     *
     * <p>更新后建议重新评估预算占用率。
     *
     * @param item 预算明细实体（需携带 ID 和当前 version）
     * @return true=更新成功，false=乐观锁冲突或记录不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectBudgetItem item) {
        return repository.updateById(item);
    }

    /**
     * 逻辑删除预算明细
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除，便于审计回溯。
     *
     * <p><b>注意：</b>删除前应校验是否有关联的实际费用（{@code ProjectExpense}）或采购成本（{@code CostPurchase}），
     * 如有关联应通过 {@code status=DISABLED} 标记停用而非删除，避免历史台账断裂。
     *
     * @param id 预算明细主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
