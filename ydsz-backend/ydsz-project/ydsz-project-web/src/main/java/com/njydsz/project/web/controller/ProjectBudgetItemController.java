package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectBudgetItem;
import com.njydsz.project.server.service.ProjectBudgetItemService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectBudgetItemVO;
import com.njydsz.project.domain.dto.put.ProjectBudgetItemPutDTO;
import com.njydsz.project.domain.dto.post.ProjectBudgetItemPostDTO;

@RestController
@RequestMapping("/api/v1/project/project/budget/item")
@RequiredArgsConstructor
public class ProjectBudgetItemController {
    private final ProjectBudgetItemService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectBudgetItemVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectBudgetItemVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectBudgetItem> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectBudgetItemListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectBudgetItem")
    public BaseResponse<Boolean> save(@RequestBody ProjectBudgetItemPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectBudgetItem")
    public BaseResponse<Boolean> update(@RequestBody ProjectBudgetItemPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectBudgetItem")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
