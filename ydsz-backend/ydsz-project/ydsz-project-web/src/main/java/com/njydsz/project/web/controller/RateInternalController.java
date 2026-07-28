package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.rate.RateInternal;
import com.njydsz.project.server.service.RateInternalService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.RateInternalVO;
import com.njydsz.project.domain.dto.post.RateInternalPostDTO;
import com.njydsz.project.domain.dto.put.RateInternalPutDTO;

/**
 * 内部费率 Controller
 *
 * <p>提供内部费率的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/rate/internal")
@RequiredArgsConstructor
public class RateInternalController {

    private final RateInternalService service;

    /**
     * 按 ID 查询内部费率
     *
     * @param id 费率主键 ID
     * @return 费率视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<RateInternalVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询内部费率列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页费率视图对象
     */
    @GetMapping("/page")
    public PageResponse<RateInternalVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<RateInternal> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.rateInternalListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建内部费率
     *
     * @param dto 费率创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create RateInternal")
    public BaseResponse<Boolean> save(@RequestBody RateInternalPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新内部费率
     *
     * @param dto 费率更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update RateInternal")
    public BaseResponse<Boolean> update(@RequestBody RateInternalPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除内部费率
     *
     * @param id 费率主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete RateInternal")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
