package com.njydsz.workflow.web.controller.definition;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.security.TenantContext;
import com.njydsz.workflow.domain.dto.FlowCategoryDTO;
import com.njydsz.workflow.domain.entity.FlowCategory;
import com.njydsz.workflow.server.service.FlowCategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程分类管理 Controller
 *
 * <p>P1-6: 对标钉钉/飞书审批的"流程分类管理"能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/workflow/categories")
@RequiredArgsConstructor
@Tag(name = "流程分类管理", description = "流程分类的增删改查")
public class FlowCategoryController {

    /** 流程分类管理服务，负责分类的增删改查 */
    private final FlowCategoryService categoryService;

    /**
     * 查询全部分类。
     *
     * @return 分类列表
     */
    @GetMapping
    @Operation(summary = "查询全部分类")
    public BaseResponse<List<FlowCategory>> list() {
        return BaseResponse.success(categoryService.listAll(TenantContext.getTenantId()));
    }

    /**
     * 新增分类。
     *
     * @param dto 分类信息
     * @return 新建分类 ID
     */
    @Idempotent(key = "ydsz:workflow:FlowCategoryController:create:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowcategory.create", threshold = 50)
    @PostMapping
    @Operation(summary = "新增分类")
    public BaseResponse<String> create(@Valid @RequestBody FlowCategoryDTO dto) {
        return BaseResponse.success(categoryService.create(dto, TenantContext.getTenantId()));
    }

    /**
     * 编辑分类。
     *
     * @param dto 分类信息
     * @return 空响应
     */
    @Idempotent(key = "ydsz:workflow:FlowCategoryController:update:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowcategory.update", threshold = 50)
    @PutMapping
    @Operation(summary = "编辑分类")
    public BaseResponse<Void> update(@Valid @RequestBody FlowCategoryDTO dto) {
        categoryService.update(dto);
        return BaseResponse.success();
    }

    /**
     * 删除分类。
     *
     * @param id 分类 ID
     * @return 空响应
     */
    @Idempotent(key = "ydsz:workflow:FlowCategoryController:delete:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowcategory.delete", threshold = 50)
    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类")
    public BaseResponse<Void> delete(@PathVariable String id) {
        categoryService.delete(id);
        return BaseResponse.success();
    }
}
