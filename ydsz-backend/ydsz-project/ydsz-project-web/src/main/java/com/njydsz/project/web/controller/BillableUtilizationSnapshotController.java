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
 * <p>提供可计费利用率快照的 REST API，是「项目管理 / 资源管理」业务域的 Controller。
 * 对标大厂 PMIS / 资源管理系统中的「人员计费率 / 资源利用率 / 资源台账」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>快照维度：</b>按 {@code period (YYYY-MM) × employeeId} 滚动生成，
 * 记录当月工时构成（可计费 / 不可计费 / 休假 / 培训）。
 *
 * <p><b>典型调用方：</b>定时任务（每月 1 号凌晨滚动生成上月快照）。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制</li>
 *   <li>快照数据为资源管理「计费率仪表盘」提供数据源</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.BillableUtilizationSnapshotService 利用率 Service
 * @see com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot 快照实体
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
