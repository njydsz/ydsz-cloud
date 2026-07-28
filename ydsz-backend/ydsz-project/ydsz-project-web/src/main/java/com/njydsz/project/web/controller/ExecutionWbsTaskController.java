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
 * <p>提供工作分解结构（WBS）任务的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
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
