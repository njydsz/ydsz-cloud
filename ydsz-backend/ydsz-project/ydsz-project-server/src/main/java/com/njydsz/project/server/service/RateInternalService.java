package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.rate.RateInternal;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 内部人员费率 Service
 *
 * <p>管理内部人员（员工）的费率档位（{@code ydsz_rate_internal}）——按"角色/职级"维度
 * 配置的内部人力成本基准，用于利润分摊、EVM 计算、计费成本核算等场景。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>按职级查询</b>：用于 {@code TimeEntry} 工时记录的自动取价</li>
 *   <li><b>按部门查询</b>：部门级费率配置</li>
 * </ul>
 *
 * <p><b>与计费卡的关系：</b>内部费率（成本侧） vs 客户计费卡（收入侧），二者按角色/部门
 * 维度配对，构成完整的"内部成本 → 客户收入"映射。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.rate.RateInternal 内部费率实体
 * @see RateCardService 客户计费卡 Service
 * @see CostAllocationService 成本分摊 Service
 */
public interface RateInternalService {
    RateInternal getById(String id);
    IPage<RateInternal> page(int pageNum, int pageSize);
    boolean save(RateInternal entity);
    boolean updateById(RateInternal entity);
    boolean removeById(String id);
}
