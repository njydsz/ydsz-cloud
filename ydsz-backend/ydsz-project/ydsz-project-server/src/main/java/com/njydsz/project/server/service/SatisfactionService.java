package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.satisfaction.Satisfaction;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 客户满意度 Service
 *
 * <p>管理客户对项目交付的满意度评价（{@code ydsz_satisfaction}）的收集与统计。
 * 满意度数据用于项目复盘、团队绩效考核、客户分级等场景。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>评价维度</b>：质量 / 进度 / 服务 / 综合(综合分由各维度加权计算)</li>
 *   <li><b>触发</b>：项目终验后自动触发客户填写</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.satisfaction.Satisfaction 满意度实体
 * @see ExecutionClosureService 项目终验 Service(终验后触发满意度)
 */
public interface SatisfactionService {
    Satisfaction getById(String id);
    IPage<Satisfaction> page(int pageNum, int pageSize);
    boolean save(Satisfaction entity);
    boolean updateById(Satisfaction entity);
    boolean removeById(String id);
}
