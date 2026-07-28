package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectCustomerCredit;
import com.njydsz.project.domain.repository.project.IProjectCustomerCreditRepository;
import com.njydsz.project.server.service.ProjectCustomerCreditService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户信用 Service 实现
 *
 * <p>对 {@link ProjectCustomerCreditService} 接口的完整实现，是「项目管理 / 客户信用管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_customer_credit} 客户信用表，
 * 对标大厂 PMIS / CRM 系统的「客户信用 / 客户评级 / 客户授信 / 客户风险」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>客户评级</b>：维护客户信用等级（{@code AAA / AA / A / BBB / BB / B / C}），
 *       由历史回款准时率 / 合同金额 / 合作年限等综合计算</li>
 *   <li><b>客户授信</b>：基于客户评级授予不同的账期 / 信用额度，
 *       支撑销售合同审批和应收账款管理</li>
 *   <li><b>客户风险监控</b>：客户信用等级变化时联动 {@link com.njydsz.project.server.service.impl.AlertDispatchServiceImpl}
 *       告警</li>
 *   <li><b>与回款联动</b>：与 {@code ydsz_project_payment} 实际回款表联动，自动计算回款准时率</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>客户评级重算时与回款表联动需在批处理任务中分批提交</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>评级周期</b>：客户评级由独立 {@code ydsz-job-cronjob} 定时任务按月重算</li>
 *   <li><b>评级不可篡改</b>：历史评级<b>严禁</b>修改，错误应通过「再评级」流程纠正</li>
 *   <li><b>数据来源</b>：与 {@code ydsz_project_payment} 实际回款数据强绑定</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       客户信用记录是合规审计的依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 月度评级重算（由定时任务调用）
 * ProjectCustomerCredit credit = new ProjectCustomerCredit();
 * credit.setCustomerId("cust_123");
 * credit.setCustomerName("某大型国企");
 * credit.setCreditLevel("AA");
 * credit.setCreditLimit(new BigDecimal("10000000"));
 * credit.setPaymentTermDays(60);
 * credit.setOnTimePaymentRate(new BigDecimal("0.95"));
 * credit.setEvaluatePeriod("2026-07");
 * projectCustomerCreditService.save(credit);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectCustomerCreditService 客户信用 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectCustomerCredit 客户信用实体
 * @see com.njydsz.project.server.service.impl.ProjectPaymentServiceImpl 回款（数据源）
 * @see com.njydsz.project.server.service.impl.AlertDispatchServiceImpl 告警派发（评级变化联动）
 */
@Service
@RequiredArgsConstructor
public class ProjectCustomerCreditServiceImpl implements ProjectCustomerCreditService {

    /** 客户信用仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectCustomerCreditRepository repository;

    /**
     * 根据主键查询客户信用
     *
     * @param id 客户信用主键
     * @return 客户信用实体，不存在返回 null
     */
    @Override
    public ProjectCustomerCredit getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询客户信用
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code customerId}、
     * {@code creditLevel}、{@code evaluatePeriod} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectCustomerCredit> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增客户信用
     *
     * <p><b>典型调用方：</b>定时任务（每月 1 号凌晨滚动重算客户评级）。
     *
     * @param credit 客户信用实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectCustomerCredit credit) {
        return repository.save(credit);
    }

    /**
     * 更新客户信用
     *
     * <p><b>注意：</b>已发布的评级（{@code status=PUBLISHED}）<b>严禁</b>修改，
     * 错误应通过「再评级」流程纠正。
     *
     * @param credit 客户信用实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectCustomerCredit credit) {
        return repository.updateById(credit);
    }

    /**
     * 逻辑删除客户信用
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>客户信用记录是合规审计的依据，<b>严禁</b>物理删除。
     *
     * @param id 客户信用主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
