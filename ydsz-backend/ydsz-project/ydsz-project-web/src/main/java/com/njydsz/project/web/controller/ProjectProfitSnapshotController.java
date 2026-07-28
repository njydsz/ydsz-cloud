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

/**
 * 利润快照 Controller
 *
 * <p>提供项目利润快照的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/profit/snapshot")
@RequiredArgsConstructor
public class ProjectProfitSnapshotController {

    private final ProjectProfitSnapshotService service;

    /**
     * 按 ID 查询利润快照
     *
     * @param id 利润快照主键 ID
     * @return 利润快照视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectProfitSnapshotVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询利润快照列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页利润快照视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectProfitSnapshotVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectProfitSnapshot> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectProfitSnapshotListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建利润快照
     *
     * @param dto 利润快照创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectProfitSnapshot")
    public BaseResponse<Boolean> save(@RequestBody ProjectProfitSnapshotPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新利润快照
     *
     * @param dto 利润快照更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectProfitSnapshot")
    public BaseResponse<Boolean> update(@RequestBody ProjectProfitSnapshotPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除利润快照
     *
     * @param id 利润快照主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectProfitSnapshot")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
