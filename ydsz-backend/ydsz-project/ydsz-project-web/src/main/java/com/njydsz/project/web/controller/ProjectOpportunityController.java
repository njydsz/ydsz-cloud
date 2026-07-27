package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectOpportunity;
import com.njydsz.project.server.service.ProjectOpportunityService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectOpportunityVO;
import com.njydsz.project.domain.dto.put.ProjectOpportunityPutDTO;
import com.njydsz.project.domain.dto.post.ProjectOpportunityPostDTO;

@RestController
@RequestMapping("/api/v1/project/project/opportunity")
@RequiredArgsConstructor
public class ProjectOpportunityController {
    private final ProjectOpportunityService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectOpportunityVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectOpportunityVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectOpportunity> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectOpportunityListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectOpportunity")
    public BaseResponse<Boolean> save(@RequestBody ProjectOpportunityPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectOpportunity")
    public BaseResponse<Boolean> update(@RequestBody ProjectOpportunityPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectOpportunity")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
