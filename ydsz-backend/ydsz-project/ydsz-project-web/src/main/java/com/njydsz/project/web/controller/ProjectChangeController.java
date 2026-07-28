package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectChange;
import com.njydsz.project.server.service.ProjectChangeService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectChangeVO;
import com.njydsz.project.domain.dto.put.ProjectChangePutDTO;
import com.njydsz.project.domain.dto.post.ProjectChangePostDTO;

/**
 * 项目变更记录 Controller
 *
 * <p>提供项目变更记录的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/change")
@RequiredArgsConstructor
public class ProjectChangeController {

    private final ProjectChangeService service;

    /**
     * 按 ID 查询变更记录
     *
     * @param id 变更记录主键 ID
     * @return 变更记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectChangeVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询变更记录列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页变更记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectChangeVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectChange> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectChangeListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建变更记录
     *
     * @param dto 变更记录创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectChange")
    public BaseResponse<Boolean> save(@RequestBody ProjectChangePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新变更记录
     *
     * @param dto 变更记录更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectChange")
    public BaseResponse<Boolean> update(@RequestBody ProjectChangePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除变更记录
     *
     * @param id 变更记录主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectChange")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
