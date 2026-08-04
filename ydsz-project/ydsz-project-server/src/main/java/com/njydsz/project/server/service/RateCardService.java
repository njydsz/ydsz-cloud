package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.rate.RateCard;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 客户计费卡 Service
 *
 * <p>管理对客户的计费卡（{@code ydsz_rate_card}）——按"客户 × 角色"维度配置的对外计费单价，
 * 用于合同报价、工时计费、收入确认等场景。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>按客户查询</b>：合同报价时取价</li>
 *   <li><b>按角色/部门查询</b>：工时自动取价</li>
 * </ul>
 *
 * <p><b>优先级：</b>客户级计费卡 > 通用计费卡 > 角色默认价。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.rate.RateCard 计费卡实体
 * @see RateInternalService 内部费率 Service
 * @see ProjectContractService 合同 Service(报价时取价)
 */
public interface RateCardService {
    RateCard getById(String id);
    IPage<RateCard> page(int pageNum, int pageSize);
    boolean save(RateCard entity);
    boolean updateById(RateCard entity);
    boolean removeById(String id);
}
