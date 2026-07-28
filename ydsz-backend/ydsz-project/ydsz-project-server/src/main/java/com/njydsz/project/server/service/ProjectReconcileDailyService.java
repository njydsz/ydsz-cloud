package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectReconcileDaily;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 项目日对账 Service
 *
 * <p>管理项目日对账单（{@code ydsz_project_reconcile_daily}）的生成与查询。
 * 日对账按"项目 × 日期"维度汇总当日的工时/费用/收入数据，是项目财务对账的最小粒度。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>日维度对账</b>：跨模块（工时/费用/收入/开票）数据汇总</li>
 *   <li><b>定时生成</b>：每日凌晨由调度器生成前一天的对账单</li>
 * </ul>
 *
 * <p><b>对账维度：</b>工时成本 / 采购费用 / 收入确认 / 应收账款。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.project.ProjectReconcileDaily 日对账实体
 * @see ProjectProfitSnapshotService 利润快照 Service(月度快照从日对账汇总)
 */
public interface ProjectReconcileDailyService {
    ProjectReconcileDaily getById(String id);
    IPage<ProjectReconcileDaily> page(int pageNum, int pageSize);
    boolean save(ProjectReconcileDaily entity);
    boolean updateById(ProjectReconcileDaily entity);
    boolean removeById(String id);
}
