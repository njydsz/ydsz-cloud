package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectProfitSnapshot;
import com.njydsz.project.server.service.ProjectProfitSnapshotService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectProfitSnapshotVO;
import com.njydsz.project.domain.dto.put.ProjectProfitSnapshotPutDTO;
import com.njydsz.project.domain.dto.post.ProjectProfitSnapshotPostDTO;

@RestController
@RequestMapping("/api/v1/project/project/profit/snapshot")
@RequiredArgsConstructor
public class ProjectProfitSnapshotController {
    private final ProjectProfitSnapshotService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectProfitSnapshotVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectProfitSnapshotVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectProfitSnapshot> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectProfitSnapshotListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", description="Create ProjectProfitSnapshot")
    public BaseResponse<Boolean> save(@RequestBody ProjectProfitSnapshotPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", description="Update ProjectProfitSnapshot")
    public BaseResponse<Boolean> update(@RequestBody ProjectProfitSnapshotPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", description="Delete ProjectProfitSnapshot")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
