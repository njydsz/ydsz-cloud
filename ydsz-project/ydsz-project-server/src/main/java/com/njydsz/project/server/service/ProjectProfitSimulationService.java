package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectProfitSimulation;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 项目利润模拟 Service
 *
 * <p>管理项目利润模拟（{@code ydsz_project_profit_simulation}）的 CRUD。
 * 利润模拟是"在项目未实际发生前,基于假设条件预测利润"的工具,常用于：
 * <ul>
 *   <li>报价前评估"按这个报价/成本/工时投入,最终利润多少"</li>
 *   <li>预算评审"如果按这个预算执行,能否达到目标毛利率"</li>
 *   <li>方案对比"两种执行方案,哪种更赚钱"</li>
 * </ul>
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>参数化模拟</b>：收入/成本/工时/费率/分摊比例可调</li>
 * </ul>
 *
 * <p><b>与快照的关系：</b>模拟是"假设"、快照是"事实"，二者解耦。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectProfitSimulation 利润模拟实体
 * @see ProjectProfitSnapshotService 利润快照 Service(对比实际利润)
 */
public interface ProjectProfitSimulationService {
    ProjectProfitSimulation getById(String id);
    IPage<ProjectProfitSimulation> page(int pageNum, int pageSize);
    boolean save(ProjectProfitSimulation entity);
    boolean updateById(ProjectProfitSimulation entity);
    boolean removeById(String id);
}
