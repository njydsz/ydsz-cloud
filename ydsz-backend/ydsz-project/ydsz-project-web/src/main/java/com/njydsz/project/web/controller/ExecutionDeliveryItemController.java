package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem;
import com.njydsz.project.server.service.ExecutionDeliveryItemService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ExecutionDeliveryItemVO;
import com.njydsz.project.domain.dto.put.ExecutionDeliveryItemPutDTO;
import com.njydsz.project.domain.dto.post.ExecutionDeliveryItemPostDTO;

@RestController
@RequestMapping("/api/v1/project/execution/delivery/item")
@RequiredArgsConstructor
public class ExecutionDeliveryItemController {
    private final ExecutionDeliveryItemService service;

    @GetMapping("/{id}")
    public BaseResponse<ExecutionDeliveryItemVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ExecutionDeliveryItemVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ExecutionDeliveryItem> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.executionDeliveryItemListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", description="Create ExecutionDeliveryItem")
    public BaseResponse<Boolean> save(@RequestBody ExecutionDeliveryItemPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", description="Update ExecutionDeliveryItem")
    public BaseResponse<Boolean> update(@RequestBody ExecutionDeliveryItemPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", description="Delete ExecutionDeliveryItem")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
