package com.njydsz.pmis.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.user.dto.RoleFormDTO;
import com.njydsz.pmis.user.dto.RoleQueryDTO;
import com.njydsz.pmis.user.entity.RoleDO;
import com.njydsz.pmis.user.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "权限-角色")
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    /** 角色服务 */
    private final RoleService roleService;

    /**
     * 角色分页查询
     *
     * @param query 查询参数
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "角色分页")
    @PrePermission("auth:role:list")
    @GetMapping
    public Result<Page<RoleDO>> page(RoleQueryDTO query) {
        return Result.ok(roleService.page(query));
    }

    /**
     * 查询所有启用的角色
     *
     * @return 统一响应结果，包含角色列表
     */
    @Operation(summary = "所有启用的角色")
    @GetMapping("/all")
    public Result<List<RoleDO>> listAll() {
        return Result.ok(roleService.listAllEnabled());
    }

    /**
     * 查询角色详情
     *
     * @param id 角色 ID
     * @return 统一响应结果，包含角色信息
     */
    @Operation(summary = "角色详情")
    @GetMapping("/{id}")
    public Result<RoleDO> get(@PathVariable Long id) {
        return Result.ok(roleService.getById(id));
    }

    /**
     * 创建角色
     *
     * @param dto 角色创建参数
     * @return 统一响应结果，包含新建角色 ID
     */
    @Operation(summary = "创建角色")
    @PrePermission("auth:role:create")
    @OperationLog(module = "权限管理", action = "创建角色", bizType = "ROLE")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody RoleFormDTO dto) {
        return Result.ok(roleService.create(dto));
    }

    /**
     * 更新角色
     *
     * @param dto 角色更新参数
     * @return 统一响应结果
     */
    @Operation(summary = "更新角色")
    @PrePermission("auth:role:update")
    @OperationLog(module = "权限管理", action = "更新角色", bizType = "ROLE")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody RoleFormDTO dto) {
        roleService.update(dto);
        return Result.ok();
    }

    /**
     * 删除角色
     *
     * @param id 角色 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除角色")
    @PrePermission("auth:role:delete")
    @OperationLog(module = "权限管理", action = "删除角色", bizType = "ROLE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }

    /**
     * 为角色分配权限
     *
     * @param id            角色 ID
     * @param permissionIds 权限 ID 列表
     * @return 统一响应结果
     */
    @Operation(summary = "为角色分配权限")
    @PrePermission("auth:role:assign")
    @OperationLog(module = "权限管理", action = "分配权限", bizType = "ROLE")
    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(id, permissionIds);
        return Result.ok();
    }

    /**
     * 查询角色的权限 ID 列表
     *
     * @param id 角色 ID
     * @return 统一响应结果，包含权限 ID 列表
     */
    @Operation(summary = "查询角色的权限 ID 列表")
    @GetMapping("/{id}/permissions")
    public Result<List<Long>> listPermissions(@PathVariable Long id) {
        return Result.ok(roleService.listPermissionIds(id));
    }
}
