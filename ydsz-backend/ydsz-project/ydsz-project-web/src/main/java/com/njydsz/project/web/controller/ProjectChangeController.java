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

@RestController
@RequestMapping("/api/v1/project/project/change")
@RequiredArgsConstructor
public class ProjectChangeController {
    private final ProjectChangeService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectChangeVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectChangeVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectChange> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectChangeListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectChange")
    public BaseResponse<Boolean> save(@RequestBody ProjectChangePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectChange")
    public BaseResponse<Boolean> update(@RequestBody ProjectChangePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectChange")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
