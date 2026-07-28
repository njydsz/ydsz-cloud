package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectOpportunity;
import com.njydsz.project.server.service.ProjectOpportunityService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectOpportunityVO;
import com.njydsz.project.domain.dto.put.ProjectOpportunityPutDTO;
import com.njydsz.project.domain.dto.post.ProjectOpportunityPostDTO;

/**
 * 项目商机 Controller
 *
 * <p>提供项目商机（销售机会）的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/opportunity")
@RequiredArgsConstructor
public class ProjectOpportunityController {

    private final ProjectOpportunityService service;

    /**
     * 按 ID 查询商机详情
     *
     * @param id 商机主键 ID
     * @return 商机视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectOpportunityVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询商机列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页商机视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectOpportunityVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectOpportunity> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectOpportunityListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建商机
     *
     * @param dto 商机创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectOpportunity")
    public BaseResponse<Boolean> save(@RequestBody ProjectOpportunityPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新商机
     *
     * @param dto 商机更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectOpportunity")
    public BaseResponse<Boolean> update(@RequestBody ProjectOpportunityPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除商机
     *
     * @param id 商机主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectOpportunity")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
