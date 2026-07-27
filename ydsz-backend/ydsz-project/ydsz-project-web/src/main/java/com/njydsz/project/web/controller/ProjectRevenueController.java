package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectRevenue;
import com.njydsz.project.server.service.ProjectRevenueService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectRevenueVO;
import com.njydsz.project.domain.dto.post.ProjectRevenuePostDTO;
import com.njydsz.project.domain.dto.put.ProjectRevenuePutDTO;

@RestController
@RequestMapping("/api/v1/project/project/revenue")
@RequiredArgsConstructor
public class ProjectRevenueController {
    private final ProjectRevenueService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectRevenueVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectRevenueVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectRevenue> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectRevenueListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectRevenue")
    public BaseResponse<Boolean> save(@RequestBody ProjectRevenuePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectRevenue")
    public BaseResponse<Boolean> update(@RequestBody ProjectRevenuePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectRevenue")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
