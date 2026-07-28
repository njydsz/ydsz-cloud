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
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.vo.FlowCategoryVO;

/**
 * 流程分类管理 Controller
 *
 * <p>对标钉钉 / 飞书审批的「流程分类管理」能力，提供流程分类的 CRUD HTTP 接口。
 * 分类数据是设计器左侧导航树、发起审批时分类筛选的数据源。
 *
 * <p><b>接口分组：</b>
 * <ul>
 *   <li><b>查询</b>：{@code GET /categories}（查询全部分类，按 sortNum 升序）</li>
 *   <li><b>新增</b>：{@code POST /categories}（创建分类）</li>
 *   <li><b>编辑</b>：{@code PUT /categories/{id}}（修改分类）</li>
 *   <li><b>删除</b>：{@code DELETE /categories/{id}}（删除分类，校验是否有子分类或关联定义）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>所有写接口通过 {@link Idempotent} 5s 防重；分类编码唯一性
 * 由 {@code @UniqueCheck} 拦截器在 Service 层校验。
 *
 * <p><b>多租户：</b>所有操作按 {@link TenantContext} 当前租户隔离，
 * 跨租户分类不可见。
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传与租户注入；分类业务逻辑下沉到
 * {@link FlowCategoryService}。树形结构由前端基于 {@code GET /categories} 扁平结果自行组装。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowCategoryService 分类服务
 * @see FlowCategory 分类实体
 * @see FlowCategoryDTO 分类 DTO
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/workflow/categories")
@RequiredArgsConstructor
@Tag(name = "流程分类管理", description = "流程分类的增删改查")
public class FlowCategoryController {

    /** 流程分类管理服务，负责分类的增删改查 */
    private final FlowCategoryService categoryService;

    /**
     * 查询全部分类
     *
     * <p>返回当前租户的全部流程分类（不构建树形结构），按 {@code sortNum} 升序、{@code id} 升序排列。
     * <p>典型场景：设计器左侧分类树加载、发起审批页分类筛选。
     * <p>前端基于扁平结果自行组装树形结构。
     *
     * @return 分类列表
     */
    @GetMapping
    @Operation(summary = "查询全部分类")
    public BaseResponse<List<FlowCategoryVO>> list() {
        return BaseResponse.success(WorkflowConverter.INSTANT.flowCategoryListToVO(categoryService.listAll(TenantContext.getTenantId())));
    }

    /**
     * 新增分类
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p>业务流程：categoryCode 唯一性校验 → 写入 DB → 清除分类缓存。
     * <p>创建根分类时 {@code parentId} 应传 {@code "0"}（约定值）。
     *
     * @param dto 分类 DTO（categoryCode / categoryName / parentId / icon / sortNum）
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
     * 编辑分类
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p>业务流程：使用 {@code BeanUpdateUtil.copyNonNull} 动态复制非 null 字段。
     * <p>修改 {@code categoryCode} 会影响设计器分类树索引，<b>需谨慎</b>。
     *
     * @param dto 分类 DTO（必须包含 ID）
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
     * 删除分类
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p>删除前置校验：
     * <ul>
     *   <li>有<b>子分类</b>的分类<b>禁止删除</b>（避免悬挂引用）</li>
     *   <li>有<b>流程定义</b>关联的分类<b>禁止删除</b></li>
     * </ul>
     * <p>如需删除带子分类的分类，<b>必须先</b>迁移子分类和流程定义。
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
