package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.rate.RateInternal;
import com.njydsz.project.server.service.RateInternalService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.RateInternalVO;
import com.njydsz.project.domain.dto.post.RateInternalPostDTO;
import com.njydsz.project.domain.dto.put.RateInternalPutDTO;

@RestController
@RequestMapping("/api/v1/project/rate/internal")
@RequiredArgsConstructor
public class RateInternalController {
    private final RateInternalService service;

    @GetMapping("/{id}")
    public BaseResponse<RateInternalVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<RateInternalVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<RateInternal> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.rateInternalListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", description="Create RateInternal")
    public BaseResponse<Boolean> save(@RequestBody RateInternalPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", description="Update RateInternal")
    public BaseResponse<Boolean> update(@RequestBody RateInternalPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", description="Delete RateInternal")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
