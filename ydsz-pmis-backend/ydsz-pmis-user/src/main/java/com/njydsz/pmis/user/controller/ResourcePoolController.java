package com.njydsz.pmis.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.user.dto.ResourcePoolCreateDTO;
import com.njydsz.pmis.user.entity.ResourcePoolDO;
import com.njydsz.pmis.user.service.ResourcePoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/resource-pools")
@RequiredArgsConstructor
public class ResourcePoolController {

    private final ResourcePoolService poolService;

    @Operation(summary = "创建资源池")
    @PrePermission("resource:pool:create")
    @OperationLog(module = "资源池", action = "创建资源池", bizType = "RESOURCE_POOL")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ResourcePoolCreateDTO dto) {
        return Result.ok(poolService.create(dto));
    }

    @Operation(summary = "更新资源池")
    @PrePermission("resource:pool:update")
    @OperationLog(module = "资源池", action = "更新资源池", bizType = "RESOURCE_POOL")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ResourcePoolCreateDTO dto) {
        poolService.update(id, dto);
        return Result.ok();
    }

    @Operation(summary = "删除资源池")
    @PrePermission("resource:pool:delete")
    @OperationLog(module = "资源池", action = "删除资源池", bizType = "RESOURCE_POOL")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        poolService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "资源池详情")
    @GetMapping("/{id}")
    public Result<ResourcePoolDO> get(@PathVariable Long id) {
        return Result.ok(poolService.getById(id));
    }

    @Operation(summary = "按类型查询")
    @GetMapping("/by-type")
    public Result<List<ResourcePoolDO>> listByType(@RequestParam String poolType) {
        return Result.ok(poolService.listByType(poolType));
    }

    @Operation(summary = "按部门查询")
    @GetMapping("/by-dept/{departmentId}")
    public Result<List<ResourcePoolDO>> listByDept(@PathVariable Long departmentId) {
        return Result.ok(poolService.listByDept(departmentId));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public Result<Page<ResourcePoolDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String poolType,
            @RequestParam(required = false) String status) {
        return Result.ok(poolService.page(page, size, poolType, status));
    }
}
