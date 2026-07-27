package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard;
import com.njydsz.project.server.service.ExecutionDeliveryStandardService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/project/execution/delivery/standard")
@RequiredArgsConstructor
public class ExecutionDeliveryStandardController {
    private final ExecutionDeliveryStandardService service;

    @GetMapping("/{id}")
    public BaseResponse<ExecutionDeliveryStandard> getById(@PathVariable String id) { return BaseResponse.success(service.getById(id)); }

    @GetMapping("/page")
    public PageResponse<ExecutionDeliveryStandard> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ExecutionDeliveryStandard> r = service.page(p, s);
        return PageResponse.success(r.getRecords(), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", description="Create ExecutionDeliveryStandard")
    public BaseResponse<Boolean> save(@RequestBody ExecutionDeliveryStandard e) { return BaseResponse.success(service.save(e)); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", description="Update ExecutionDeliveryStandard")
    public BaseResponse<Boolean> update(@RequestBody ExecutionDeliveryStandard e) { return BaseResponse.success(service.updateById(e)); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", description="Delete ExecutionDeliveryStandard")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
