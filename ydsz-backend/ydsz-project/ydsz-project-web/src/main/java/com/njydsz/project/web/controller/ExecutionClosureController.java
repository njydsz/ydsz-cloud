package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.execution.ExecutionClosure;
import com.njydsz.project.server.service.ExecutionClosureService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ExecutionClosureVO;
import com.njydsz.project.domain.dto.post.ExecutionClosurePostDTO;
import com.njydsz.project.domain.dto.put.ExecutionClosurePutDTO;

/**
 * 项目结项 Controller
 *
 * <p>提供项目结项记录的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/execution/closure")
@RequiredArgsConstructor
public class ExecutionClosureController {

    private final ExecutionClosureService service;

    /**
     * 按 ID 查询结项记录
     *
     * @param id 结项记录主键 ID
     * @return 结项记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ExecutionClosureVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询结项记录列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页结项记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<ExecutionClosureVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ExecutionClosure> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.executionClosureListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建结项记录
     *
     * @param dto 结项记录创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ExecutionClosure")
    public BaseResponse<Boolean> save(@RequestBody ExecutionClosurePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新结项记录
     *
     * @param dto 结项记录更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ExecutionClosure")
    public BaseResponse<Boolean> update(@RequestBody ExecutionClosurePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除结项记录
     *
     * @param id 结项记录主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ExecutionClosure")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
