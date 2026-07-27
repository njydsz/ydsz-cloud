package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectOpportunityFollow;
import com.njydsz.project.server.service.ProjectOpportunityFollowService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectOpportunityFollowVO;
import com.njydsz.project.domain.dto.put.ProjectOpportunityFollowPutDTO;
import com.njydsz.project.domain.dto.post.ProjectOpportunityFollowPostDTO;

@RestController
@RequestMapping("/api/v1/project/project/opportunity/follow")
@RequiredArgsConstructor
public class ProjectOpportunityFollowController {
    private final ProjectOpportunityFollowService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectOpportunityFollowVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectOpportunityFollowVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectOpportunityFollow> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectOpportunityFollowListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", description="Create ProjectOpportunityFollow")
    public BaseResponse<Boolean> save(@RequestBody ProjectOpportunityFollowPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", description="Update ProjectOpportunityFollow")
    public BaseResponse<Boolean> update(@RequestBody ProjectOpportunityFollowPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", description="Delete ProjectOpportunityFollow")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
