package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.project.ProjectCustomerCredit;
/**
 * 客户信用 Service
 *
 * <p>管理客户信用档案（{@code ydsz_project_customer_credit}）的评估、调整、查询。</p>
 * <p>信用档案是合同评审/垫资/赊销的关键依据：客户的信用等级、授信额度、账期、历史回款表现等，</p>
 * <p>决定了能否签订大额合同、能否赊销、是否需要预付款。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>信用评估：按历史回款/合同金额/账期计算信用分</b></li>
 *   <li><b>授信额度：客户在授信额度内可赊销</b></li>
 *   <li><b>信用预警：失信/严重逾期触发预警</b></li>
 * </ul>
 *
 * <p><b>信用等级：</b>AAA / AA / A / BBB / BB / B / C（数字越低信用越好）。
 * <p><b>评估维度：</b>回款及时率 / 平均账期 / 历史逾期次数 / 合同金额规模 / 合作年限。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectCustomerCredit 客户信用实体
 * @see ProjectContractService 合同 Service(信用档案是合同评审依据)
 */
public interface ProjectCustomerCreditService {
    ProjectCustomerCredit getById(String id);
    IPage<ProjectCustomerCredit> page(int pageNum, int pageSize);
    boolean save(ProjectCustomerCredit entity);
    boolean updateById(ProjectCustomerCredit entity);
    boolean removeById(String id);
}
