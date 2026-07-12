package com.njydsz.pmis.userinfo.web.controller.resource;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.userinfo.domain.dto.resource.ResourcePoolCreateDTO;
import com.njydsz.pmis.userinfo.domain.entity.resource.ResourcePoolDO;
import com.njydsz.pmis.userinfo.server.service.resource.ResourcePoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资源池 Controller
 *
 * <p>三级资源池（总部/事业部/备用）管理。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "资源池管理")
@RestController
@RequestMapping("/resourcePools")
@RequiredArgsConstructor
@Validated
public class ResourcePoolController {

    /** 资源池服务 */
    private final ResourcePoolService poolService;

    /**
     * 创建资源池
     *
     * @param dto 资源池创建参数
     * @return 统一响应结果，包含新建资源池 ID
     */
    @Operation(summary = "创建资源池")
    @PrePermission("resource:pool:create")
    @OperationLog(module = "资源池", action = "创建资源池", bizType = "RESOURCE_POOL")
    @Idempotent(key = "resourcePool:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody ResourcePoolCreateDTO dto) {
        return BaseResponse.ok(poolService.create(dto));
    }

    /**
     * 更新资源池
     *
     * @param id  资源池 ID
     * @param dto 资源池更新参数
     * @return 统一响应结果
     */
    @Operation(summary = "更新资源池")
    @PrePermission("resource:pool:update")
    @OperationLog(module = "资源池", action = "更新资源池", bizType = "RESOURCE_POOL")
    @Idempotent(key = "resourcePool:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    public BaseResponse<Void> update(@PathVariable String id, @Valid @RequestBody ResourcePoolCreateDTO dto) {
        poolService.update(id, dto);
        return BaseResponse.ok();
    }

    /**
     * 删除资源池
     *
     * @param id 资源池 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除资源池")
    @PrePermission("resource:pool:delete")
    @OperationLog(module = "资源池", action = "删除资源池", bizType = "RESOURCE_POOL")
    @Idempotent(key = "resourcePool:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        poolService.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询资源池详情
     *
     * @param id 资源池 ID
     * @return 统一响应结果，包含资源池信息
     */
    @Operation(summary = "资源池详情")
    @GetMapping("/{id}")
    public BaseResponse<ResourcePoolDO> get(@PathVariable String id) {
        return BaseResponse.ok(poolService.getById(id));
    }

    /**
     * 按类型查询资源池
     *
     * @param poolType 资源池类型
     * @return 统一响应结果，包含资源池列表
     */
    @Operation(summary = "按类型查询")
    @GetMapping("/byType")
    public BaseResponse<List<ResourcePoolDO>> listByType(@RequestParam String poolType) {
        return BaseResponse.ok(poolService.listByType(poolType));
    }

    /**
     * 按部门查询资源池
     *
     * @param departmentId 部门 ID
     * @return 统一响应结果，包含资源池列表
     */
    @Operation(summary = "按部门查询")
    @GetMapping("/byDept/{departmentId}")
    public BaseResponse<List<ResourcePoolDO>> listByDept(@PathVariable String departmentId) {
        return BaseResponse.ok(poolService.listByDept(departmentId));
    }

    /**
     * 分页查询资源池
     *
     * @param page     页码
     * @param size     每页大小
     * @param poolType 资源池类型（可选）
     * @param status   状态（可选）
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public BaseResponse<Page<ResourcePoolDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String poolType,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(poolService.page(page, size, poolType, status));
    }
}
