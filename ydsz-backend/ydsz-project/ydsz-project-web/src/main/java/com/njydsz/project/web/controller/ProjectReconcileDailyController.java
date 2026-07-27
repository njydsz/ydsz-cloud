package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectReconcileDaily;
import com.njydsz.project.server.service.ProjectReconcileDailyService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectReconcileDailyVO;

@RestController
@RequestMapping("/api/v1/project/project/reconcile/daily")
@RequiredArgsConstructor
public class ProjectReconcileDailyController {
    private final ProjectReconcileDailyService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectReconcileDailyVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectReconcileDailyVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectReconcileDaily> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectReconcileDailyListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", description="Create ProjectReconcileDaily")
    public BaseResponse<Boolean> save(@RequestBody ProjectReconcileDaily e) { return BaseResponse.success(service.save(e)); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", description="Update ProjectReconcileDaily")
    public BaseResponse<Boolean> update(@RequestBody ProjectReconcileDaily e) { return BaseResponse.success(service.updateById(e)); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", description="Delete ProjectReconcileDaily")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
