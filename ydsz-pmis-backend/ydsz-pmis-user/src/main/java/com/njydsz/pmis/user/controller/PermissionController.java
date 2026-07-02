package com.njydsz.pmis.user.controller;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.user.dto.PermissionFormDTO;
import com.njydsz.pmis.user.entity.PermissionDO;
import com.njydsz.pmis.user.service.PermissionService;
import com.njydsz.pmis.user.vo.MenuTreeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限/菜单接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "权限-权限/菜单")
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "查询所有权限")
    @GetMapping
    public Result<List<PermissionDO>> list() {
        return Result.ok(permissionService.listAllEnabled());
    }

    @Operation(summary = "查询当前用户权限编码")
    @GetMapping("/mine")
    public Result<List<String>> mine(@RequestHeader("X-User-Id") Long userId) {
        return Result.ok(permissionService.listPermCodesByUserId(userId));
    }

    @Operation(summary = "查询当前用户菜单树")
    @GetMapping("/menu-tree")
    public Result<List<MenuTreeVO>> menuTree(@RequestHeader("X-User-Id") Long userId) {
        return Result.ok(permissionService.listMenuTreeByUserId(userId));
    }

    @Operation(summary = "查询所有权限(构建树)")
    @GetMapping("/tree")
    public Result<List<MenuTreeVO>> tree() {
        return Result.ok(permissionService.listAllMenuTree());
    }

    @Operation(summary = "查询角色的权限")
    @GetMapping("/by-role/{roleId}")
    public Result<List<PermissionDO>> listByRole(@PathVariable Long roleId) {
        return Result.ok(permissionService.listByRoleId(roleId));
    }

    @Operation(summary = "权限详情")
    @GetMapping("/{id}")
    public Result<PermissionDO> get(@PathVariable Long id) {
        return Result.ok(permissionService.getById(id));
    }

    @Operation(summary = "创建权限")
    @PrePermission("auth:perm:create")
    @OperationLog(module = "权限管理", action = "创建权限", bizType = "PERM")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PermissionFormDTO dto) {
        return Result.ok(permissionService.create(dto));
    }

    @Operation(summary = "更新权限")
    @PrePermission("auth:perm:update")
    @OperationLog(module = "权限管理", action = "更新权限", bizType = "PERM")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody PermissionFormDTO dto) {
        permissionService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除权限")
    @PrePermission("auth:perm:delete")
    @OperationLog(module = "权限管理", action = "删除权限", bizType = "PERM")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return Result.ok();
    }
}
