package com.njydsz.pmis.workflow.web.controller.definition;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.workflow.domain.dto.definition.FlowCategoryDTO;
import com.njydsz.pmis.workflow.domain.entity.definition.FlowCategoryDO;
import com.njydsz.pmis.workflow.server.service.definition.FlowCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程分类管理 Controller
 *
 * <p>P1-6: 对标钉钉/飞书审批的"流程分类管理"能力。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
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
    public BaseResponse<List<FlowCategoryDO>> list() {
        return BaseResponse.ok(categoryService.listAll(TenantContext.getTenantId()));
    }

    /**
     * 新增分类。
     *
     * @param dto 分类信息
     * @return 新建分类 ID
     */
    @Idempotent(key = "flowCategory:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    @Operation(summary = "新增分类")
    public BaseResponse<String> create(@Valid @RequestBody FlowCategoryDTO dto) {
        return BaseResponse.ok(categoryService.create(dto, TenantContext.getTenantId()));
    }

    /**
     * 编辑分类。
     *
     * @param dto 分类信息
     * @return 空响应
     */
    @Idempotent(key = "flowCategory:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping
    @Operation(summary = "编辑分类")
    public BaseResponse<Void> update(@Valid @RequestBody FlowCategoryDTO dto) {
        categoryService.update(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除分类。
     *
     * @param id 分类 ID
     * @return 空响应
     */
    @Idempotent(key = "flowCategory:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类")
    public BaseResponse<Void> delete(@PathVariable String id) {
        categoryService.delete(id);
        return BaseResponse.ok();
    }
}
