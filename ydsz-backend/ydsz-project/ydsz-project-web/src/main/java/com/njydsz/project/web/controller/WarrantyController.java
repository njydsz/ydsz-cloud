package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.warranty.Warranty;
import com.njydsz.project.server.service.WarrantyService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.WarrantyVO;
import com.njydsz.project.domain.dto.post.WarrantyPostDTO;
import com.njydsz.project.domain.dto.put.WarrantyPutDTO;

@RestController
@RequestMapping("/api/v1/project/warranty")
@RequiredArgsConstructor
public class WarrantyController {
    private final WarrantyService service;

    @GetMapping("/{id}")
    public BaseResponse<WarrantyVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<WarrantyVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<Warranty> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.warrantyListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create Warranty")
    public BaseResponse<Boolean> save(@RequestBody WarrantyPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update Warranty")
    public BaseResponse<Boolean> update(@RequestBody WarrantyPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete Warranty")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
