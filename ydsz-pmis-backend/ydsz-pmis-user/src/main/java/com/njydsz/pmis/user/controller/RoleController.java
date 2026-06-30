package com.njydsz.pmis.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
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

    private final RoleService roleService;

    @Operation(summary = "角色分页")
    @PrePermission("auth:role:list")
    @GetMapping
    public R<Page<RoleDO>> page(RoleQueryDTO query) {
        return R.ok(roleService.page(query));
    }

    @Operation(summary = "所有启用的角色")
    @GetMapping("/all")
    public R<List<RoleDO>> listAll() {
        return R.ok(roleService.listAllEnabled());
    }

    @Operation(summary = "角色详情")
    @GetMapping("/{id}")
    public R<RoleDO> get(@PathVariable Long id) {
        return R.ok(roleService.getById(id));
    }

    @Operation(summary = "创建角色")
    @PrePermission("auth:role:create")
    @OperationLog(module = "权限管理", action = "创建角色", bizType = "ROLE")
    @PostMapping
    public R<Long> create(@Valid @RequestBody RoleFormDTO dto) {
        return R.ok(roleService.create(dto));
    }

    @Operation(summary = "更新角色")
    @PrePermission("auth:role:update")
    @OperationLog(module = "权限管理", action = "更新角色", bizType = "ROLE")
    @PutMapping
    public R<Void> update(@Valid @RequestBody RoleFormDTO dto) {
        roleService.update(dto);
        return R.ok();
    }

    @Operation(summary = "删除角色")
    @PrePermission("auth:role:delete")
    @OperationLog(module = "权限管理", action = "删除角色", bizType = "ROLE")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }

    @Operation(summary = "为角色分配权限")
    @PrePermission("auth:role:assign")
    @OperationLog(module = "权限管理", action = "分配权限", bizType = "ROLE")
    @PutMapping("/{id}/permissions")
    public R<Void> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(id, permissionIds);
        return R.ok();
    }

    @Operation(summary = "查询角色的权限 ID 列表")
    @GetMapping("/{id}/permissions")
    public R<List<Long>> listPermissions(@PathVariable Long id) {
        return R.ok(roleService.listPermissionIds(id));
    }
}
