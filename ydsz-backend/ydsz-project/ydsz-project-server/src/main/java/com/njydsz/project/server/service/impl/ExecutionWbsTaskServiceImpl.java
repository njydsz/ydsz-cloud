package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.execution.ExecutionWbsTask;
import com.njydsz.project.domain.repository.execution.IExecutionWbsTaskRepository;
import com.njydsz.project.server.service.ExecutionWbsTaskService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WBS 任务 Service 实现
 *
 * <p>对 {@link ExecutionWbsTaskService} 接口的完整实现，是「项目管理 / 项目执行」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_execution_wbs_task} WBS 任务表，
 * 对标大厂 PMIS / 项目管理系统的「WBS（Work Breakdown Structure）/ 工作分解结构 / 项目任务」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>WBS 任务分解</b>：将项目交付物分解为层级化的工作包（{@code parentId} 形成树形结构），
 *       支撑甘特图 / 责任分配矩阵（RAM）的可视化</li>
 *   <li><b>任务依赖</b>：通过 {@code predecessorIds} 字段记录任务前置依赖（FS / SS / FF 关系），
 *       支撑关键路径分析（CPM）</li>
 *   <li><b>进度跟踪</b>：通过 {@code progressPct}（完成百分比）跟踪任务进度，
 *       联动 {@code ydsz_evm_measure} 计算挣值（EV）</li>
 *   <li><b>责任分配</b>：通过 {@code ownerId}（责任人）/ {@code contributorIds}（参与人）字段分配任务</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>WBS 批量导入 / 模板初始化时按子任务分批事务提交</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>层级结构</b>：通过 {@code parentId} 自引用实现树形结构，
 *       推荐层级不超过 5 层（避免用户认知负担）</li>
 *   <li><b>WBS 编码</b>：{@code wbsCode} 字段（如 {@code 1.2.3}）作为业务主键，
 *       在全项目内唯一</li>
 *   <li><b>里程碑任务</b>：{@code isMilestone} 字段标识里程碑任务（{@code duration=0}），
 *       用于阶段门评审</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       任务记录是项目复盘的依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 立项初始化时创建 WBS 根任务
 * ExecutionWbsTask root = new ExecutionWbsTask();
 * root.setInitiationId("project_123");
 * root.setWbsCode("1");
 * root.setTaskName("某 ERP 实施项目");
 * root.setParentId("");
 * root.setLevel(1);
 * root.setStartDate(LocalDate.of(2026, 8, 1));
 * root.setEndDate(LocalDate.of(2026, 12, 31));
 * executionWbsTaskService.save(root);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ExecutionWbsTaskService WBS 任务 Service 接口
 * @see com.njydsz.project.domain.entity.execution.ExecutionWbsTask WBS 任务实体
 * @see com.njydsz.project.server.service.impl.EvmMeasureServiceImpl EVM 度量（PV 来源）
 * @see com.njydsz.project.server.service.impl.ExecutionTimeEntryServiceImpl 工时录入（实际工时）
 */
@Service
@RequiredArgsConstructor
public class ExecutionWbsTaskServiceImpl implements ExecutionWbsTaskService {

    /** WBS 任务仓储（聚合 Mapper + 缓存 + 事件） */
    private final IExecutionWbsTaskRepository repository;

    /**
     * 根据主键查询 WBS 任务
     *
     * @param id WBS 任务主键
     * @return WBS 任务实体，不存在返回 null
     */
    @Override
    public ExecutionWbsTask getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询 WBS 任务
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code initiationId}、
     * {@code parentId}、{@code ownerId} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ExecutionWbsTask> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增 WBS 任务
     *
     * @param task WBS 任务实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ExecutionWbsTask task) {
        return repository.save(task);
    }

    /**
     * 更新 WBS 任务
     *
     * <p>典型场景：更新任务进度、调整工期、补充责任人。
     *
     * @param task WBS 任务实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ExecutionWbsTask task) {
        return repository.updateById(task);
    }

    /**
     * 逻辑删除 WBS 任务
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>任务记录是项目复盘的依据，<b>严禁</b>物理删除。
     * 子任务应先删除，再删除父任务。
     *
     * @param id WBS 任务主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
