package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.warranty.Warranty;
import com.njydsz.project.server.service.WarrantyService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.WarrantyVO;
import com.njydsz.project.domain.dto.post.WarrantyPostDTO;
import com.njydsz.project.domain.dto.put.WarrantyPutDTO;

/**
 * 质保 Controller
 *
 * <p>提供质保记录的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/warranty")
@RequiredArgsConstructor
public class WarrantyController {

    private final WarrantyService service;

    /**
     * 按 ID 查询质保记录
     *
     * @param id 质保记录主键 ID
     * @return 质保记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<WarrantyVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询质保记录列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页质保记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<WarrantyVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<Warranty> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.warrantyListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建质保记录
     *
     * @param dto 质保记录创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create Warranty")
    public BaseResponse<Boolean> save(@RequestBody WarrantyPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新质保记录
     *
     * @param dto 质保记录更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update Warranty")
    public BaseResponse<Boolean> update(@RequestBody WarrantyPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除质保记录
     *
     * @param id 质保记录主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete Warranty")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
