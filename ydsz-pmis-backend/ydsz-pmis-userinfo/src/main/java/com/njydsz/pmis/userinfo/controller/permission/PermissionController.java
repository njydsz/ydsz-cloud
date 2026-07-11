package com.njydsz.pmis.userinfo.controller.permission;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.userinfo.dto.permission.PermissionFormDTO;
import com.njydsz.pmis.userinfo.entity.permission.PermissionDO;
import com.njydsz.pmis.userinfo.service.permission.PermissionService;
import com.njydsz.pmis.userinfo.vo.MenuTreeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限/菜单接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "权限管理", description = "权限管理相关接口")
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@Validated
public class PermissionController {

    /** 权限服务 */
    private final PermissionService permissionService;

    /**
     * 查询所有启用的权限
     *
     * @return 统一响应结果，包含权限列表
     */
    @Operation(summary = "查询所有权限")
    @GetMapping
    public Result<List<PermissionDO>> list() {
        return Result.ok(permissionService.listAllEnabled());
    }

    /**
     * 查询当前用户权限编码
     *
     * @param userId 用户 ID（由网关透传）
     * @return 统一响应结果，包含权限编码列表
     */
    @Operation(summary = "查询当前用户权限编码")
    @GetMapping("/mine")
    public Result<List<String>> mine(@RequestHeader("X-User-Id") String userId) {
        return Result.ok(permissionService.listPermCodesByUserId(userId));
    }

    /**
     * 查询当前用户菜单树
     *
     * @param userId 用户 ID（由网关透传）
     * @return 统一响应结果，包含菜单树
     */
    @Operation(summary = "查询当前用户菜单树")
    @GetMapping("/menuTree")
    public Result<List<MenuTreeVO>> menuTree(@RequestHeader("X-User-Id") String userId) {
        return Result.ok(permissionService.listMenuTreeByUserId(userId));
    }

    /**
     * 查询所有权限并构建为树形结构
     *
     * @return 统一响应结果，包含菜单树
     */
    @Operation(summary = "查询所有权限(构建树)")
    @GetMapping("/tree")
    public Result<List<MenuTreeVO>> tree() {
        return Result.ok(permissionService.listAllMenuTree());
    }

    /**
     * 查询角色已分配的权限
     *
     * @param roleId 角色 ID
     * @return 统一响应结果，包含权限列表
     */
    @Operation(summary = "查询角色的权限")
    @GetMapping("/byRole/{roleId}")
    public Result<List<PermissionDO>> listByRole(@Parameter(description = "角色ID") @PathVariable String roleId) {
        return Result.ok(permissionService.listByRoleId(roleId));
    }

    /**
     * 查询权限详情
     *
     * @param id 权限 ID
     * @return 统一响应结果，包含权限信息
     */
    @Operation(summary = "权限详情")
    @GetMapping("/{id}")
    public Result<PermissionDO> get(@Parameter(description = "权限ID") @PathVariable String id) {
        return Result.ok(permissionService.getById(id));
    }

    /**
     * 创建权限
     *
     * @param dto 权限表单
     * @return 统一响应结果，包含新建权限 ID
     */
    @Operation(summary = "创建权限")
    @PrePermission("auth:perm:create")
    @OperationLog(module = "权限管理", action = "创建权限", bizType = "PERM")
    @Idempotent(key = "permission:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> create(@Valid @RequestBody PermissionFormDTO dto) {
        return Result.ok(permissionService.create(dto));
    }

    /**
     * 更新权限
     *
     * @param dto 权限表单
     * @return 统一响应结果
     */
    @Operation(summary = "更新权限")
    @PrePermission("auth:perm:update")
    @OperationLog(module = "权限管理", action = "更新权限", bizType = "PERM")
    @Idempotent(key = "permission:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody PermissionFormDTO dto) {
        permissionService.update(dto);
        return Result.ok();
    }

    /**
     * 删除权限
     *
     * @param id 权限 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除权限")
    @PrePermission("auth:perm:delete")
    @OperationLog(module = "权限管理", action = "删除权限", bizType = "PERM")
    @Idempotent(key = "permission:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "权限ID") @PathVariable String id) {
        permissionService.delete(id);
        return Result.ok();
    }
}
