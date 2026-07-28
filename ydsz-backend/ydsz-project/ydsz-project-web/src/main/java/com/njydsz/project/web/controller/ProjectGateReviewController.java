package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectGateReview;
import com.njydsz.project.server.service.ProjectGateReviewService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectGateReviewVO;
import com.njydsz.project.domain.dto.put.ProjectGateReviewPutDTO;
import com.njydsz.project.domain.dto.post.ProjectGateReviewPostDTO;

/**
 * 门径评审 Controller
 *
 * <p>提供项目门径评审记录的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/gate/review")
@RequiredArgsConstructor
public class ProjectGateReviewController {

    private final ProjectGateReviewService service;

    /**
     * 按 ID 查询评审记录
     *
     * @param id 评审记录主键 ID
     * @return 评审记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectGateReviewVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询评审记录列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页评审记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectGateReviewVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectGateReview> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectGateReviewListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建评审记录
     *
     * @param dto 评审记录创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectGateReview")
    public BaseResponse<Boolean> save(@RequestBody ProjectGateReviewPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新评审记录
     *
     * @param dto 评审记录更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectGateReview")
    public BaseResponse<Boolean> update(@RequestBody ProjectGateReviewPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除评审记录
     *
     * @param id 评审记录主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectGateReview")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
