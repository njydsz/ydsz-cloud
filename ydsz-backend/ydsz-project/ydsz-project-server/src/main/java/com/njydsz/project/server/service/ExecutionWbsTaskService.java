package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.execution.ExecutionWbsTask;
/**
 * 项目 WBS 任务 Service
 *
 * <p>管理项目 WBS 任务（{@code ydsz_execution_wbs_task}）的分解、分配、跟踪。</p>
 * <p>WBS（Work Breakdown Structure）是项目工作分解结构，将项目交付物逐层拆分为可执行的任务，</p>
 * <p>用于项目计划/进度跟踪/工时填报/成本归集。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>任务分解：父子任务层级（项目→阶段→任务→子任务）</b></li>
 *   <li><b>责任人分配：每个任务指定责任人</b></li>
 *   <li><b>进度更新：实时更新完成度/状态</b></li>
 * </ul>
 *
 * <p><b>任务状态：</b>TODO / IN_PROGRESS / BLOCKED / REVIEW / DONE / CANCELLED。
 * <p><b>任务字段：</b>计划开始/结束/实际开始/结束/工时预估/实际工时/优先级。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.execution.ExecutionWbsTask WBS 任务实体
 * @see ExecutionTimeEntryService 工时填报 Service(任务关联工时)
 * @see ProjectInitiationService 立项 Service(WBS 挂载在项目下)
 */
public interface ExecutionWbsTaskService {
    ExecutionWbsTask getById(String id);
    IPage<ExecutionWbsTask> page(int pageNum, int pageSize);
    boolean save(ExecutionWbsTask entity);
    boolean updateById(ExecutionWbsTask entity);
    boolean removeById(String id);
}
