package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.evm.EvmMeasure;
import com.njydsz.project.server.service.EvmMeasureService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.EvmMeasureVO;
import com.njydsz.project.domain.dto.post.EvmMeasurePostDTO;
import com.njydsz.project.domain.dto.put.EvmMeasurePutDTO;

/**
 * EVM 挣值测量 Controller
 *
 * <p>提供挣值管理（EVM）测量记录的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/evm/measure")
@RequiredArgsConstructor
public class EvmMeasureController {

    private final EvmMeasureService service;

    /**
     * 按 ID 查询挣值测量
     *
     * @param id 测量记录主键 ID
     * @return 测量记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<EvmMeasureVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询挣值测量列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页测量记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<EvmMeasureVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<EvmMeasure> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.evmMeasureListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建挣值测量
     *
     * @param dto 测量记录创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create EvmMeasure")
    public BaseResponse<Boolean> save(@RequestBody EvmMeasurePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新挣值测量
     *
     * @param dto 测量记录更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update EvmMeasure")
    public BaseResponse<Boolean> update(@RequestBody EvmMeasurePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除挣值测量
     *
     * @param id 测量记录主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete EvmMeasure")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
