package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.cost.CostPurchase;
import com.njydsz.project.domain.repository.cost.ICostPurchaseRepository;
import com.njydsz.project.server.service.CostPurchaseService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目采购成本 Service 实现
 *
 * <p>对 {@link CostPurchaseService} 接口的完整实现，是「项目管理 / 采购成本」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_cost_purchase} 采购成本申请表，
 * 对标大厂 PMIS / ERP 系统的「项目采购 / 采购申请 / 采购成本归集」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>采购申请</b>：项目执行过程中的硬件 / 软件 / 服务采购申请，含采购类型 / 数量 / 单价 / 供应商</li>
 *   <li><b>预算占用校验</b>：采购审批时联动 {@code ydsz_project_budget_item} 预算明细校验占用率，
 *       触发 80% 黄灯 / 95% 红灯预警</li>
 *   <li><b>成本归集</b>：采购完成后联动 {@code ydsz_cost_allocation} 成本归集表，按期间归集</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>采购审批通过后联动预算占用更新需与预算 Service 共享同一事务</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>审批流</b>：超过阈值的采购需走 {@code ydsz-workflow} 流程引擎审批</li>
 *   <li><b>三单匹配</b>：采购申请（{@code PR}） / 采购订单（{@code PO}） / 采购入库（{@code GR}）
 *       三单匹配校验，避免虚假采购</li>
 *   <li><b>供应商管理</b>：联动 {@code ydsz-supplier} 供应商主数据（独立模块），
 *       不在本表维护供应商字段</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       采购记录是财务核算的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 创建采购申请
 * CostPurchase purchase = new CostPurchase();
 * purchase.setInitiationId("project_123");
 * purchase.setPurchaseType("HARDWARE");
 * purchase.setItemName("Dell 服务器");
 * purchase.setQuantity(2);
 * purchase.setUnitPrice(new BigDecimal("50000"));
 * purchase.setTotalAmount(new BigDecimal("100000"));
 * purchase.setSupplierId("supplier_456");
 * purchase.setStatus("PENDING");
 * costPurchaseService.save(purchase);
 *
 * // 2. 审批通过后联动预算占用
 * // 由 ydsz-workflow 流程实例审批通过事件回调触发
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see CostPurchaseService 采购成本 Service 接口
 * @see com.njydsz.project.domain.entity.cost.CostPurchase 采购成本实体
 * @see com.njydsz.project.server.service.impl.ProjectBudgetItemServiceImpl 预算明细（占用联动）
 * @see com.njydsz.project.server.service.impl.CostAllocationServiceImpl 成本归集（数据消费方）
 */
@Service
@RequiredArgsConstructor
public class CostPurchaseServiceImpl implements CostPurchaseService {

    /** 采购成本仓储（聚合 Mapper + 缓存 + 事件） */
    private final ICostPurchaseRepository repository;

    /**
     * 根据主键查询采购
     *
     * @param id 采购主键
     * @return 采购实体，不存在返回 null
     */
    @Override
    public CostPurchase getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询采购
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code purchaseType}、{@code status} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<CostPurchase> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增采购申请
     *
     * <p>新增后应触发 {@code PurchaseCreatedEvent} 领域事件，
     * 由 {@code ydsz-workflow} 流程引擎启动采购审批流 + 预算占用校验。
     *
     * @param purchase 采购实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(CostPurchase purchase) {
        return repository.save(purchase);
    }

    /**
     * 更新采购
     *
     * <p><b>注意：</b>仅在审批中（{@code status=PENDING}）的采购可修改，
     * 审批通过后（{@code status=APPROVED}）的采购<b>严禁</b>修改关键字段。
     *
     * @param purchase 采购实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(CostPurchase purchase) {
        return repository.updateById(purchase);
    }

    /**
     * 逻辑删除采购
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>采购记录是财务核算的法定依据，<b>严禁</b>物理删除。
     *
     * @param id 采购主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
