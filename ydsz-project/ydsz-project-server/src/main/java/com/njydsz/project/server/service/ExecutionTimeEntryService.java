package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.execution.ExecutionTimeEntry;
/**
 * 工时填报 Service
 *
 * <p>管理员工工时填报（{@code ydsz_execution_time_entry}）的录入与汇总。</p>
 * <p>工时是项目成本的主要组成（人力成本占项目成本 60%+），按员工 × 日期 × 任务 维度填报，</p>
 * <p>用于项目成本核算、计费、利润分析。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>按任务/员工/日期汇总：用于成本归集</b></li>
 *   <li><b>按月报工时：自动统计月工时/月计费</b></li>
 *   <li><b>审批流：超过 8h/天 或 40h/周 触发审批</b></li>
 * </ul>
 *
 * <p><b>计费模式：</b>正常 / 加班(1.5x) / 周末(2x) / 节假日(3x)。
 * <p><b>取价规则：</b>按任务的 {@link RateCardService} 客户计费 + {@link RateInternalService} 内部费率。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.execution.ExecutionTimeEntry 工时实体
 * @see ExecutionWbsTaskService WBS 任务 Service(工时关联任务)
 * @see ProjectProfitSnapshotService 利润快照 Service(工时是利润数据源)
 */
public interface ExecutionTimeEntryService {
    ExecutionTimeEntry getById(String id);
    IPage<ExecutionTimeEntry> page(int pageNum, int pageSize);
    boolean save(ExecutionTimeEntry entity);
    boolean updateById(ExecutionTimeEntry entity);
    boolean removeById(String id);
}
