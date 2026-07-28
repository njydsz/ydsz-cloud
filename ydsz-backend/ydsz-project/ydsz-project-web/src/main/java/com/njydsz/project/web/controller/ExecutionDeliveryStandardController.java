package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard;
import com.njydsz.project.server.service.ExecutionDeliveryStandardService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ExecutionDeliveryStandardVO;
import com.njydsz.project.domain.dto.post.ExecutionDeliveryStandardPostDTO;
import com.njydsz.project.domain.dto.put.ExecutionDeliveryStandardPutDTO;

/**
 * 交付标准 Controller
 *
 * <p>提供项目交付标准的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/execution/delivery/standard")
@RequiredArgsConstructor
public class ExecutionDeliveryStandardController {

    private final ExecutionDeliveryStandardService service;

    /**
     * 按 ID 查询交付标准
     *
     * @param id 交付标准主键 ID
     * @return 交付标准视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ExecutionDeliveryStandardVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询交付标准列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页交付标准视图对象
     */
    @GetMapping("/page")
    public PageResponse<ExecutionDeliveryStandardVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ExecutionDeliveryStandard> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.executionDeliveryStandardListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建交付标准
     *
     * @param dto 交付标准创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ExecutionDeliveryStandard")
    public BaseResponse<Boolean> save(@RequestBody ExecutionDeliveryStandardPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新交付标准
     *
     * @param dto 交付标准更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ExecutionDeliveryStandard")
    public BaseResponse<Boolean> update(@RequestBody ExecutionDeliveryStandardPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除交付标准
     *
     * @param id 交付标准主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ExecutionDeliveryStandard")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
