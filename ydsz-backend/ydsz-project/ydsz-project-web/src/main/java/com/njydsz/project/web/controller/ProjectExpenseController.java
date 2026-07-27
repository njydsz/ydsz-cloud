package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectExpense;
import com.njydsz.project.server.service.ProjectExpenseService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectExpenseVO;
import com.njydsz.project.domain.dto.put.ProjectExpensePutDTO;
import com.njydsz.project.domain.dto.post.ProjectExpensePostDTO;

@RestController
@RequestMapping("/api/v1/project/project/expense")
@RequiredArgsConstructor
public class ProjectExpenseController {
    private final ProjectExpenseService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectExpenseVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectExpenseVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectExpense> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectExpenseListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectExpense")
    public BaseResponse<Boolean> save(@RequestBody ProjectExpensePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectExpense")
    public BaseResponse<Boolean> update(@RequestBody ProjectExpensePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectExpense")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
