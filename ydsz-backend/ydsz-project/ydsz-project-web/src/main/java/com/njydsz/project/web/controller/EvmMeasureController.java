package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.evm.EvmMeasure;
import com.njydsz.project.server.service.EvmMeasureService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.EvmMeasureVO;
import com.njydsz.project.domain.dto.post.EvmMeasurePostDTO;
import com.njydsz.project.domain.dto.put.EvmMeasurePutDTO;

@RestController
@RequestMapping("/api/v1/project/evm/measure")
@RequiredArgsConstructor
public class EvmMeasureController {
    private final EvmMeasureService service;

    @GetMapping("/{id}")
    public BaseResponse<EvmMeasureVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<EvmMeasureVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<EvmMeasure> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.evmMeasureListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create EvmMeasure")
    public BaseResponse<Boolean> save(@RequestBody EvmMeasurePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update EvmMeasure")
    public BaseResponse<Boolean> update(@RequestBody EvmMeasurePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete EvmMeasure")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
