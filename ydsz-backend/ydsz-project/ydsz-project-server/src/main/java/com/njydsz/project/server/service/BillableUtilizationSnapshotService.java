package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot;
/**
 * 可计费率快照 Service
 *
 * <p>管理可计费率快照（{@code ydsz_billable_utilization_snapshot}）的生成与查询。</p>
 * <p>可计费率 = 计费工时 / 总工时，反映员工/部门时间投入在有收入项目上的比例，</p>
 * <p>是衡量团队产能与项目健康度的核心指标。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>按员工/部门/项目维度汇总</b></li>
 *   <li><b>快照化：按月/季度固化,避免数据漂移</b></li>
 * </ul>
 *
 * <p><b>指标定义：</b>可计费率 = ∑(计费工时) / ∑(总工时)。
 * <p><b>健康阈值：</b>可计费率 ≥ 75% 为健康, < 60% 触发预警。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot 可计费率快照实体
 * @see ExecutionTimeEntryService 工时 Service(数据源)
 */
public interface BillableUtilizationSnapshotService {
    BillableUtilizationSnapshot getById(String id);
    IPage<BillableUtilizationSnapshot> page(int pageNum, int pageSize);
    boolean save(BillableUtilizationSnapshot entity);
    boolean updateById(BillableUtilizationSnapshot entity);
    boolean removeById(String id);
}
