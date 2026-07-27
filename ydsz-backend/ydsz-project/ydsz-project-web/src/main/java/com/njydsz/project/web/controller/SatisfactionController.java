package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.satisfaction.Satisfaction;
import com.njydsz.project.server.service.SatisfactionService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.SatisfactionVO;
import com.njydsz.project.domain.dto.put.SatisfactionPutDTO;
import com.njydsz.project.domain.dto.post.SatisfactionPostDTO;

@RestController
@RequestMapping("/api/v1/project/satisfaction")
@RequiredArgsConstructor
public class SatisfactionController {
    private final SatisfactionService service;

    @GetMapping("/{id}")
    public BaseResponse<SatisfactionVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<SatisfactionVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<Satisfaction> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.satisfactionListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create Satisfaction")
    public BaseResponse<Boolean> save(@RequestBody SatisfactionPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update Satisfaction")
    public BaseResponse<Boolean> update(@RequestBody SatisfactionPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete Satisfaction")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
