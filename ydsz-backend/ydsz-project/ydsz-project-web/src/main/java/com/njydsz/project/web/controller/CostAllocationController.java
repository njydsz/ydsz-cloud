package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.cost.CostAllocation;
import com.njydsz.project.server.service.CostAllocationService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.CostAllocationVO;
import com.njydsz.project.domain.dto.post.CostAllocationPostDTO;
import com.njydsz.project.domain.dto.put.CostAllocationPutDTO;

@RestController
@RequestMapping("/api/v1/project/cost/allocation")
@RequiredArgsConstructor
public class CostAllocationController {
    private final CostAllocationService service;

    @GetMapping("/{id}")
    public BaseResponse<CostAllocationVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<CostAllocationVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<CostAllocation> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.costAllocationListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create CostAllocation")
    public BaseResponse<Boolean> save(@RequestBody CostAllocationPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update CostAllocation")
    public BaseResponse<Boolean> update(@RequestBody CostAllocationPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete CostAllocation")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
