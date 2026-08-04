package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.cost.CostAllocation;
/**
 * 成本分摊 Service
 *
 * <p>管理成本分摊（{@code ydsz_cost_allocation}）规则的配置与执行。</p>
 * <p>成本分摊是把公共成本按规则分摊到具体项目/部门的过程，常用于：</p>
 * <p><ul><li>跨项目使用的人员费用</li><li>跨部门使用的平台/工具费用</li><li>管理费用的二次分摊</li></ul>
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>分摊规则：按比例/按工时/按收入 等多种分摊方式</b></li>
 *   <li><b>分摊执行：定时执行分摊并写入项目</b></li>
 * </ul>
 *
 * <p><b>分摊方式：</b>按工时比例 / 按收入比例 / 按人数比例 / 固定比例。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.cost.CostAllocation 成本分摊实体
 * @see ExecutionTimeEntryService 工时 Service(按工时分摊的数据源)
 * @see ProjectProfitSnapshotService 利润快照 Service(分摊后计入利润)
 */
public interface CostAllocationService {
    CostAllocation getById(String id);
    IPage<CostAllocation> page(int pageNum, int pageSize);
    boolean save(CostAllocation entity);
    boolean updateById(CostAllocation entity);
    boolean removeById(String id);
}
