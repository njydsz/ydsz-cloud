package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectChange;
import com.njydsz.project.server.service.ProjectChangeService;

import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectChangeVO;
import com.njydsz.project.domain.dto.put.ProjectChangePutDTO;
import com.njydsz.project.domain.dto.post.ProjectChangePostDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 项目变更记录 Controller
 *
 * <p>提供项目变更申请的 REST API，是「项目管理 / 项目变更管理」业务域的 Controller。
 * 对标大厂 PMIS / 项目管理系统中的「项目变更 / 项目调整 / 项目变更申请」管理界面。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #update} /
 *       {@link #remove}</li>
 * </ul>
 *
 * <p><b>变更类型：</b>SCOPE 范围 / SCHEDULE 工期 / BUDGET 预算 / RESOURCE 资源 / OTHER 其他。
 *
 * <p><b>审批集成：</b>每条变更申请对应一个 {@code ydsz-workflow} 流程实例，
 * 由 {@code ydsz-workflow} 流程引擎驱动审批流。
 *
 * <p><b>安全控制：</b>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制</li>
 *   <li>变更审批通过后联动 WBS 任务 / 预算明细 / 项目阶段自动调整</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ProjectChangeService 变更 Service
 * @see com.njydsz.project.domain.entity.project.ProjectChange 变更实体
 */
@RestController
@RequestMapping("/api/v1/project/project/change")
@RequiredArgsConstructor
public class ProjectChangeController {

    private final ProjectChangeService service;

    /**
     * 按 ID 查询变更记录
     *
     * @param id 变更记录主键 ID
     * @return 变更记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectChangeVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询变更记录列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页变更记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<List<ProjectChangeVO>> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectChange> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectChangeListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建变更记录
     *
     * @param dto 变更记录创建入参
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ProjectChangeController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectChange")
    public BaseResponse<Boolean> save(@RequestBody ProjectChangePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新变更记录
     *
     * @param dto 变更记录更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ProjectChangeController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectChange")
    public BaseResponse<Boolean> update(@RequestBody ProjectChangePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除变更记录
     *
     * @param id 变更记录主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ProjectChangeController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectChange")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
