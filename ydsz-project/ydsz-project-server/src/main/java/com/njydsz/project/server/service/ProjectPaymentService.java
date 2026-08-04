package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectPayment;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 项目回款 Service
 *
 * <p>管理项目回款（{@code ydsz_project_payment}）的入账、核销、对账。
 * 回款是客户按合同条款实际支付的项目款项,核销后进入利润计算。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>回款核销</b>：关联到具体发票/合同,核销应收账款</li>
 *   <li><b>逾期跟踪</b>：标记逾期回款,触发催收</li>
 * </ul>
 *
 * <p><b>回款方式：</b>银行转账 / 商业票据 / 第三方支付(如支付宝/微信企业付款)。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectPayment 回款实体
 * @see ProjectInvoiceService 发票 Service(回款核销发票)
 * @see ProjectRevenueService 收入 Service(回款触发收入确认)
 */
public interface ProjectPaymentService {
    ProjectPayment getById(String id);
    IPage<ProjectPayment> page(int pageNum, int pageSize);
    boolean save(ProjectPayment entity);
    boolean updateById(ProjectPayment entity);
    boolean removeById(String id);
}
