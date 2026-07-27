package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot;
import com.njydsz.project.server.service.BillableUtilizationSnapshotService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.BillableUtilizationSnapshotVO;

@RestController
@RequestMapping("/api/v1/project/billable/utilization/snapshot")
@RequiredArgsConstructor
public class BillableUtilizationSnapshotController {
    private final BillableUtilizationSnapshotService service;

    @GetMapping("/{id}")
    public BaseResponse<BillableUtilizationSnapshotVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<BillableUtilizationSnapshotVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<BillableUtilizationSnapshot> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.billableUtilizationSnapshotListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", description="Create BillableUtilizationSnapshot")
    public BaseResponse<Boolean> save(@RequestBody BillableUtilizationSnapshot e) { return BaseResponse.success(service.save(e)); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", description="Update BillableUtilizationSnapshot")
    public BaseResponse<Boolean> update(@RequestBody BillableUtilizationSnapshot e) { return BaseResponse.success(service.updateById(e)); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", description="Delete BillableUtilizationSnapshot")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
