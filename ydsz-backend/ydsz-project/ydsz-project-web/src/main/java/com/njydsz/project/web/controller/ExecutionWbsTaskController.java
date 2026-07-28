package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.execution.ExecutionWbsTask;
import com.njydsz.project.server.service.ExecutionWbsTaskService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ExecutionWbsTaskVO;
import com.njydsz.project.domain.dto.put.ExecutionWbsTaskPutDTO;
import com.njydsz.project.domain.dto.post.ExecutionWbsTaskPostDTO;

/**
 * WBS 任务 Controller。
 *
 * <p>提供工作分解结构（WBS）任务的 REST API，是「项目管理 / 项目执行」业务域的核心 Controller。
 * 对标大厂 PMIS / 项目管理系统（如 Jira / Trello / 飞书项目）中的「WBS（Work Breakdown Structure）/
 * 工作分解结构 / 项目任务 / 任务计划」管理界面。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>WBS 分解：将项目交付物分解为层级化的工作包（{@code parentId} 形成树形结构）</li>
 *   <li>任务依赖：通过 {@code predecessorIds} 字段记录任务前置依赖（FS / SS / FF / SF 关系）</li>
 *   <li>进度跟踪：通过 {@code progressPct}（完成百分比）跟踪任务进度，自动汇总至父任务</li>
 *   <li>资源分配：每个任务可分配负责人（{@code ownerId}）和参与者（{@code assigneeIds}）</li>
 *   <li>工时登记：关联 {@code ExecutionTimeEntry} 记录实际工时</li>
 *   <li>关键路径：自动计算 CPM（关键路径）并高亮展示</li>
 * </ul>
 *
 * <h3>WBS 状态机</h3>
 * <pre>
 *  TODO → IN_PROGRESS → REVIEWING → COMPLETED
 *  (待办)   (进行中)      (验收中)    (已完成)
 *               ↓
 *            BLOCKED (阻塞，需解除后回到 IN_PROGRESS)
 *               ↓
 *            CANCELLED (取消)
 * </pre>
 *
 * <h3>关键约束</h3>
 * <ul>
 *   <li>已完成（{@code status=COMPLETED}）的任务<b>严禁</b>直接修改进度</li>
 *   <li>删除任务前需先解除所有依赖关系（{@code predecessorIds}）</li>
 *   <li>WBS 变更需走项目变更流程，任务负责人仅可修改自己负责的任务</li>
 *   <li>进度更新自动触发项目挣值（EVM）计算</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制</li>
 *   <li>WBS 树查询深度限制为 10 层，防止无限递归</li>
 *   <li>{@code @Valid} 触发 JSR-303 校验</li>
 * </ul>
 *
 * <h3>接口路径</h3>
 * <pre>
 *   GET    /api/v1/project/execution/wbs/task/{id}   - 按 ID 查询
 *   GET    /api/v1/project/execution/wbs/task/page   - 分页查询
 *   POST   /api/v1/project/execution/wbs/task        - 创建任务
 *   PUT    /api/v1/project/execution/wbs/task        - 更新任务
 *   DELETE /api/v1/project/execution/wbs/task/{id}   - 删除任务
 * </pre>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-project-web (本 Controller)
 *                                          ↓
 *                              ydsz-project-server.ExecutionWbsTaskService
 *                              ydsz-project-server.WbsProgressAggregator
 *                              ydsz-project-server.EvmCalculateService (进度更新触发)
 *                                          ↓
 *                              ydsz-project-infra.ExecutionWbsTaskMapper
 *                                          ↓
 *                              ydsz_execution_wbs_task
 *                              ydsz_execution_time_entry (工时)
 *                              ydsz_evm_measure (EVM)
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ExecutionWbsTaskService WBS Service
 * @see com.njydsz.project.domain.entity.execution.ExecutionWbsTask WBS 任务实体
 * @see ExecutionTimeEntryController 工时录入 Controller
 * @see EvmMeasureController EVM 挣值 Controller
 */
@RestController
@RequestMapping("/api/v1/project/execution/wbs/task")
@RequiredArgsConstructor
public class ExecutionWbsTaskController {

    private final ExecutionWbsTaskService service;

    /**
     * 按 ID 查询 WBS 任务。
     *
     * <p>返回任务实体 + 富化的负责人名称 / 项目名称 / 父任务名称等外键字段。
     *
     * @param id 任务主键 ID
     * @return 任务视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ExecutionWbsTaskVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询 WBS 任务列表。
     *
     * <p>支持按项目、负责人、状态、计划起止日期等多维度筛选；按 WBS 编码排序返回。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页任务视图对象
     */
    @GetMapping("/page")
    public PageResponse<ExecutionWbsTaskVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ExecutionWbsTask> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.executionWbsTaskListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建 WBS 任务。
     *
     * <p>通常从项目立项 / 项目计划阶段批量导入；创建后自动：
     * <ol>
     *   <li>校验父任务存在性（{@code parentId}）</li>
     *   <li>校验依赖关系不形成环（{@code predecessorIds}）</li>
     *   <li>分配 WBS 编码（{@code wbsCode}，按父编码 + 子序号自动生成）</li>
     * </ol>
     *
     * @param dto 任务创建入参（项目 ID、父任务 ID、负责人、起止日期、依赖任务等）
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ExecutionWbsTask")
    public BaseResponse<Boolean> save(@RequestBody ExecutionWbsTaskPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新 WBS 任务。
     *
     * <p>已完成（{@code status=COMPLETED}）的任务<b>严禁</b>修改关键字段（工期、依赖关系）。
     * 进度更新（{@code progressPct}）会触发父任务进度自动汇总与 EVM 重算。
     *
     * @param dto 任务更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ExecutionWbsTask")
    public BaseResponse<Boolean> update(@RequestBody ExecutionWbsTaskPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除 WBS 任务。
     *
     * <p>采用<b>逻辑删除</b>；存在子任务或被其他任务依赖时<b>严禁</b>删除。
     * 删除前需先解除所有依赖关系并删除子任务。
     *
     * @param id 任务主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ExecutionWbsTask")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
