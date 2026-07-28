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
import com.njydsz.project.domain.dto.post.BillableUtilizationSnapshotPostDTO;
import com.njydsz.project.domain.dto.put.BillableUtilizationSnapshotPutDTO;

/**
 * 可计费利用率快照 Controller
 *
 * <p>提供可计费利用率快照的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/billable/utilization/snapshot")
@RequiredArgsConstructor
public class BillableUtilizationSnapshotController {

    private final BillableUtilizationSnapshotService service;

    /**
     * 按 ID 查询利用率快照
     *
     * @param id 快照主键 ID
     * @return 快照视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<BillableUtilizationSnapshotVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询利用率快照列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页快照视图对象
     */
    @GetMapping("/page")
    public PageResponse<BillableUtilizationSnapshotVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<BillableUtilizationSnapshot> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.billableUtilizationSnapshotListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建利用率快照
     *
     * @param dto 快照创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create BillableUtilizationSnapshot")
    public BaseResponse<Boolean> save(@RequestBody BillableUtilizationSnapshotPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新利用率快照
     *
     * @param dto 快照更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update BillableUtilizationSnapshot")
    public BaseResponse<Boolean> update(@RequestBody BillableUtilizationSnapshotPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除利用率快照
     *
     * @param id 快照主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete BillableUtilizationSnapshot")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
