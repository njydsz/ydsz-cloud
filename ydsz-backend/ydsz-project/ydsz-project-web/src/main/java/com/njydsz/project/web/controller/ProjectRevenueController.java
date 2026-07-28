package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectRevenue;
import com.njydsz.project.server.service.ProjectRevenueService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectRevenueVO;
import com.njydsz.project.domain.dto.post.ProjectRevenuePostDTO;
import com.njydsz.project.domain.dto.put.ProjectRevenuePutDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 项目收入 Controller
 *
 * <p>提供项目收入的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/revenue")
@RequiredArgsConstructor
public class ProjectRevenueController {

    private final ProjectRevenueService service;

    /**
     * 按 ID 查询收入详情
     *
     * @param id 收入主键 ID
     * @return 收入视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectRevenueVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询收入列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页收入视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectRevenueVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectRevenue> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectRevenueListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建收入
     *
     * @param dto 收入创建入参
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ProjectRevenueController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectRevenue")
    public BaseResponse<Boolean> save(@RequestBody ProjectRevenuePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新收入
     *
     * @param dto 收入更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ProjectRevenueController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectRevenue")
    public BaseResponse<Boolean> update(@RequestBody ProjectRevenuePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除收入
     *
     * @param id 收入主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ProjectRevenueController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectRevenue")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
