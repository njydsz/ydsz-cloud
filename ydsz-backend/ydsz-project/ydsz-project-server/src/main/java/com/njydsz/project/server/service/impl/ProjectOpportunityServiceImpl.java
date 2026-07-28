package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectOpportunity;
import com.njydsz.project.domain.repository.project.IProjectOpportunityRepository;
import com.njydsz.project.server.service.ProjectOpportunityService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目商机 Service 实现
 *
 * <p>对 {@link ProjectOpportunityService} 接口的完整实现，是「项目管理 / 销售管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_opportunity} 商机主表，
 * 对标大厂 PMIS / CRM 系统的「商机管理 / 销售漏斗 / 销售预测 / 合同前机会管理」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>销售漏斗</b>：商机按阶段（{@code LEAD} 线索 / {@code QUALIFIED} 验证 /
 *       {@code PROPOSAL} 提案 / {@code NEGOTIATION} 谈判 / {@code WON} 赢 /
 *       {@code LOST} 输）流转，支撑销售漏斗可视化</li>
 *   <li><b>赢率管理</b>：根据商机阶段和客户反馈动态调整赢率（{@code winRate}），
 *       支撑销售预测</li>
 *   <li><b>商机分级</b>：按预计金额和赢率划分 A / B / C 级，A 级商机优先资源投入</li>
 *   <li><b>转化立项</b>：商机成熟（{@code WON}）后转化为 {@code ydsz_project_initiation} 立项</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>商机转化立项时联动立项 Service 需在同一事务</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>客户关联</b>：通过 {@code customerId} 关联客户主数据（{@code ydsz-customer}，独立模块）</li>
 *   <li><b>竞争对手</b>：通过 {@code competitors} 字段记录竞争对手情况，
 *       支撑竞争分析</li>
 *   <li><b>预计金额</b>：{@code estimatedAmount} 由销售录入，联动客户信用额度校验</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       商机是销售预测和审计的依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 创建商机
 * ProjectOpportunity opp = new ProjectOpportunity();
 * opp.setOppCode("OPP-2026-001");
 * opp.setOppName("某大型 ERP 实施项目");
 * opp.setCustomerId("cust_123");
 * opp.setStage("QUALIFIED");
 * opp.setEstimatedAmount(new BigDecimal("5000000"));
 * opp.setWinRate(new BigDecimal("0.4"));
 * opp.setOwnerId("user_sales_001");
 * opp.setCloseDate(LocalDate.of(2026, 12, 31));
 * projectOpportunityService.save(opp);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectOpportunityService 商机 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectOpportunity 商机实体
 * @see com.njydsz.project.server.service.impl.ProjectOpportunityFollowServiceImpl 商机跟进 Service
 * @see com.njydsz.project.server.service.impl.ProjectInitiationServiceImpl 立项 Service（商机转化）
 */
@Service
@RequiredArgsConstructor
public class ProjectOpportunityServiceImpl implements ProjectOpportunityService {

    /** 商机仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectOpportunityRepository repository;

    /**
     * 根据主键查询商机
     *
     * @param id 商机主键
     * @return 商机实体，不存在返回 null
     */
    @Override
    public ProjectOpportunity getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询商机
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code customerId}、
     * {@code stage}、{@code ownerId} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectOpportunity> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增商机
     *
     * @param opportunity 商机实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectOpportunity opportunity) {
        return repository.save(opportunity);
    }

    /**
     * 更新商机
     *
     * <p>典型场景：调整阶段、更新赢率、补充客户反馈、录入竞争对手。
     *
     * @param opportunity 商机实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectOpportunity opportunity) {
        return repository.updateById(opportunity);
    }

    /**
     * 逻辑删除商机
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>商机是销售预测和审计的依据，<b>严禁</b>物理删除。
     * 输掉的商机应通过 {@code status=LOST} 标记而非删除。
     *
     * @param id 商机主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
