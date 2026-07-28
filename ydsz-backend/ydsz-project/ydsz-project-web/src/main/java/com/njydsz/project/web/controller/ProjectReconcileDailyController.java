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
import com.njydsz.project.domain.dto.put.ProjectReconcileDailyPutDTO;
import com.njydsz.project.domain.dto.post.ProjectReconcileDailyPostDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 日对账 Controller
 *
 * <p>提供项目日对账记录的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/reconcile/daily")
@RequiredArgsConstructor
public class ProjectReconcileDailyController {

    private final ProjectReconcileDailyService service;

    /**
     * 按 ID 查询对账记录
     *
     * @param id 对账记录主键 ID
     * @return 对账记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectReconcileDailyVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询对账记录列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页对账记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectReconcileDailyVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectReconcileDaily> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectReconcileDailyListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建对账记录
     *
     * @param dto 对账记录创建入参
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ProjectReconcileDailyController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectReconcileDaily")
    public BaseResponse<Boolean> save(@RequestBody ProjectReconcileDailyPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新对账记录
     *
     * @param dto 对账记录更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ProjectReconcileDailyController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectReconcileDaily")
    public BaseResponse<Boolean> update(@RequestBody ProjectReconcileDailyPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除对账记录
     *
     * @param id 对账记录主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ProjectReconcileDailyController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectReconcileDaily")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
