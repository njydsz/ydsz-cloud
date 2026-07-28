package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectProfitSnapshot;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 项目利润快照 Service
 *
 * <p>管理项目利润快照（{@code ydsz_project_profit_snapshot}）的生成与查询。
 * 利润快照按"项目 × 月"维度从日对账/工时/费用/收入数据汇总计算,固化每月利润数据,
 * 用于利润分析、复盘、考核。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>快照生成</b>：每月初由调度器从日对账汇总生成</li>
 *   <li><b>不可变性</b>：快照一旦生成不再变更,保证历史可追溯</li>
 * </ul>
 *
 * <p><b>快照字段：</b>总收入 / 总成本 / 毛利 / 毛利率 / 工时成本 / 采购费用 / 应收账款余额。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectProfitSnapshot 利润快照实体
 * @see ProjectReconcileDailyService 日对账 Service(快照数据源)
 */
public interface ProjectProfitSnapshotService {
    ProjectProfitSnapshot getById(String id);
    IPage<ProjectProfitSnapshot> page(int pageNum, int pageSize);
    boolean save(ProjectProfitSnapshot entity);
    boolean updateById(ProjectProfitSnapshot entity);
    boolean removeById(String id);
}
