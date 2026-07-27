package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectContractChange;
import com.njydsz.project.server.service.ProjectContractChangeService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectContractChangeVO;
import com.njydsz.project.domain.dto.put.ProjectContractChangePutDTO;
import com.njydsz.project.domain.dto.post.ProjectContractChangePostDTO;

@RestController
@RequestMapping("/api/v1/project/project/contract/change")
@RequiredArgsConstructor
public class ProjectContractChangeController {
    private final ProjectContractChangeService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectContractChangeVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectContractChangeVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectContractChange> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectContractChangeListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectContractChange")
    public BaseResponse<Boolean> save(@RequestBody ProjectContractChangePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectContractChange")
    public BaseResponse<Boolean> update(@RequestBody ProjectContractChangePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectContractChange")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
