package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.cost.CostPurchase;
import com.njydsz.project.server.service.CostPurchaseService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.CostPurchaseVO;
import com.njydsz.project.domain.dto.post.CostPurchasePostDTO;
import com.njydsz.project.domain.dto.put.CostPurchasePutDTO;

/**
 * 采购成本 Controller
 *
 * <p>提供采购成本记录的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/cost/purchase")
@RequiredArgsConstructor
public class CostPurchaseController {

    private final CostPurchaseService service;

    /**
     * 按 ID 查询采购成本
     *
     * @param id 采购成本主键 ID
     * @return 采购成本视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<CostPurchaseVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询采购成本列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页采购成本视图对象
     */
    @GetMapping("/page")
    public PageResponse<CostPurchaseVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<CostPurchase> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.costPurchaseListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建采购成本
     *
     * @param dto 采购成本创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create CostPurchase")
    public BaseResponse<Boolean> save(@RequestBody CostPurchasePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新采购成本
     *
     * @param dto 采购成本更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update CostPurchase")
    public BaseResponse<Boolean> update(@RequestBody CostPurchasePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除采购成本
     *
     * @param id 采购成本主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete CostPurchase")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
