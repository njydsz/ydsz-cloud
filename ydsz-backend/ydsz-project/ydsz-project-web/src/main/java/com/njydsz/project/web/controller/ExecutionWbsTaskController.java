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
 * WBS 任务 Controller
 *
 * <p>提供工作分解结构（WBS）任务的 REST API，是「项目管理 / 项目执行」业务域的 Controller。
 * 对标大厂 PMIS / 项目管理系统中的「WBS（Work Breakdown Structure）/ 工作分解结构 / 项目任务」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>WBS 任务分解：</b>将项目交付物分解为层级化的工作包（{@code parentId} 形成树形结构）。
 *
 * <p><b>任务依赖：</b>通过 {@code predecessorIds} 字段记录任务前置依赖（FS / SS / FF 关系）。
 *
 * <p><b>进度跟踪：</b>通过 {@code progressPct}（完成百分比）跟踪任务进度。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制</li>
 *   <li>WBS 变更需走项目变更流程，任务负责人仅可修改自己负责的任务</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ExecutionWbsTaskService WBS Service
 * @see com.njydsz.project.domain.entity.execution.ExecutionWbsTask WBS 任务实体
 */
@RestController
@RequestMapping("/api/v1/project/execution/wbs/task")
@RequiredArgsConstructor
public class ExecutionWbsTaskController {

    private final ExecutionWbsTaskService service;

    /**
     * 按 ID 查询 WBS 任务
     *
     * @param id 任务主键 ID
     * @return 任务视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ExecutionWbsTaskVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询 WBS 任务列表
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
     * 创建 WBS 任务
     *
     * @param dto 任务创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ExecutionWbsTask")
    public BaseResponse<Boolean> save(@RequestBody ExecutionWbsTaskPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新 WBS 任务
     *
     * @param dto 任务更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ExecutionWbsTask")
    public BaseResponse<Boolean> update(@RequestBody ExecutionWbsTaskPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除 WBS 任务
     *
     * @param id 任务主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ExecutionWbsTask")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
