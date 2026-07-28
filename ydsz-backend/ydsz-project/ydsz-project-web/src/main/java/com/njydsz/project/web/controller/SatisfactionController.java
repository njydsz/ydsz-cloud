package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.satisfaction.Satisfaction;
import com.njydsz.project.server.service.SatisfactionService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.SatisfactionVO;
import com.njydsz.project.domain.dto.put.SatisfactionPutDTO;
import com.njydsz.project.domain.dto.post.SatisfactionPostDTO;

/**
 * 满意度 Controller
 *
 * <p>提供满意度评价的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/satisfaction")
@RequiredArgsConstructor
public class SatisfactionController {

    private final SatisfactionService service;

    /**
     * 按 ID 查询满意度评价
     *
     * @param id 评价主键 ID
     * @return 评价视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<SatisfactionVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询满意度评价列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页评价视图对象
     */
    @GetMapping("/page")
    public PageResponse<SatisfactionVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<Satisfaction> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.satisfactionListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建满意度评价
     *
     * @param dto 评价创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create Satisfaction")
    public BaseResponse<Boolean> save(@RequestBody SatisfactionPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新满意度评价
     *
     * @param dto 评价更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update Satisfaction")
    public BaseResponse<Boolean> update(@RequestBody SatisfactionPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除满意度评价
     *
     * @param id 评价主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete Satisfaction")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
