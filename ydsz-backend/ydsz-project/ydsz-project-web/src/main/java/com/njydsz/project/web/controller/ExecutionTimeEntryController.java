package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.execution.ExecutionTimeEntry;
import com.njydsz.project.server.service.ExecutionTimeEntryService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ExecutionTimeEntryVO;
import com.njydsz.project.domain.dto.post.ExecutionTimeEntryPostDTO;
import com.njydsz.project.domain.dto.put.ExecutionTimeEntryPutDTO;

@RestController
@RequestMapping("/api/v1/project/execution/time/entry")
@RequiredArgsConstructor
public class ExecutionTimeEntryController {
    private final ExecutionTimeEntryService service;

    @GetMapping("/{id}")
    public BaseResponse<ExecutionTimeEntryVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ExecutionTimeEntryVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ExecutionTimeEntry> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.executionTimeEntryListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", description="Create ExecutionTimeEntry")
    public BaseResponse<Boolean> save(@RequestBody ExecutionTimeEntryPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", description="Update ExecutionTimeEntry")
    public BaseResponse<Boolean> update(@RequestBody ExecutionTimeEntryPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", description="Delete ExecutionTimeEntry")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
