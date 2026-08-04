package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.rate.RateCard;
import com.njydsz.project.domain.repository.rate.IRateCardRepository;
import com.njydsz.project.server.service.RateCardService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对外报价费率卡 Service 实现
 *
 * <p>对 {@link RateCardService} 接口的完整实现，是「项目管理 / 商务报价」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_rate_card} 对外报价费率卡表，
 * 对标大厂 PMIS / 商务报价系统中的「对外报价 / 客户报价单 / 标准费率卡 / 行业报价」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>标准报价</b>：按角色 / 级别 / 行业维护公司级对外报价标准</li>
 *   <li><b>客户报价</b>：支持针对特定客户定制专属报价（覆盖标准报价）</li>
 *   <li><b>报价有效期</b>：维护报价生效日期 / 失效日期，支撑报价时效管理</li>
 *   <li><b>报价审批</b>：报价变更需经商务 / 财务审批</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>报价审批通过后联动历史报价失效</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>优先级</b>：{@code priority} 区分报价优先级，客户专属报价 > 行业报价 > 标准报价</li>
 *   <li><b>币种</b>：支持多币种（CNY / USD / EUR / HKD 等），按汇率换算</li>
 *   <li><b>报价阶梯</b>：支持按工时 / 人月 / 项目总价三种报价模式</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       报价记录是商务合规的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 销售创建客户专属报价
 * RateCard card = new RateCard();
 * card.setRateCode("CARD-2026-VIP-A");
 * card.setCustomerId("cust_vip_001");
 * card.setRoleLevel("P7");
 * card.setRateAmount(new BigDecimal("2500"));
 * card.setCurrency("CNY");
 * card.setUnit("MAN_DAY");
 * card.setEffectiveDate(LocalDate.of(2026, 7, 1));
 * card.setExpiryDate(LocalDate.of(2026, 12, 31));
 * card.setStatus("ACTIVE");
 * rateCardService.save(card);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see RateCardService 报价费率卡 Service 接口
 * @see com.njydsz.project.domain.entity.rate.RateCard 报价费率卡实体
 * @see com.njydsz.project.server.service.impl.RateInternalServiceImpl 内部费率（成本基准）
 * @see com.njydsz.project.server.service.impl.ProjectProfitSimulationServiceImpl 利润模拟（报价测算）
 */
@Service
@RequiredArgsConstructor
public class RateCardServiceImpl implements RateCardService {

    /** 报价费率卡仓储（聚合 Mapper + 缓存 + 事件） */
    private final IRateCardRepository repository;

    /**
     * 根据主键查询报价费率卡
     *
     * @param id 报价费率卡主键
     * @return 报价费率卡实体，不存在返回 null
     */
    @Override
    public RateCard getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询报价费率卡
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code customerId}、
     * {@code rateCode}、{@code status} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<RateCard> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增报价费率卡
     *
     * <p>新增后应触发 {@code RateCardCreatedEvent} 领域事件，
     * 由 {@code ydsz-workflow} 流程引擎启动报价审批流。
     *
     * @param rateCard 报价费率卡实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(RateCard rateCard) {
        return repository.save(rateCard);
    }

    /**
     * 更新报价费率卡
     *
     * <p><b>注意：</b>已生效的报价（{@code status=ACTIVE}）的关键字段（金额 / 有效期）
     * <b>严禁</b>直接修改，错误应通过「作废 + 新增」流程纠正。
     *
     * @param rateCard 报价费率卡实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(RateCard rateCard) {
        return repository.updateById(rateCard);
    }

    /**
     * 逻辑删除报价费率卡
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>报价记录是商务合规的法定依据，<b>严禁</b>物理删除。
     *
     * @param id 报价费率卡主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
