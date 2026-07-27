package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.execution.ExecutionRisk;
import com.njydsz.project.server.service.ExecutionRiskService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ExecutionRiskVO;
import com.njydsz.project.domain.dto.put.ExecutionRiskPutDTO;
import com.njydsz.project.domain.dto.post.ExecutionRiskPostDTO;

@RestController
@RequestMapping("/api/v1/project/execution/risk")
@RequiredArgsConstructor
public class ExecutionRiskController {
    private final ExecutionRiskService service;

    @GetMapping("/{id}")
    public BaseResponse<ExecutionRiskVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ExecutionRiskVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ExecutionRisk> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.executionRiskListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ExecutionRisk")
    public BaseResponse<Boolean> save(@RequestBody ExecutionRiskPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ExecutionRisk")
    public BaseResponse<Boolean> update(@RequestBody ExecutionRiskPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ExecutionRisk")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
