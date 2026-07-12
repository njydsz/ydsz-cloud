package com.njydsz.pmis.userinfo.web.controller.permission;

import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.common.lock.annotation.YdszLock;

import com.njydsz.pmis.common.audit.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.userinfo.domain.dto.permission.PermissionFormDTO;
import com.njydsz.pmis.userinfo.domain.entity.permission.PermissionDO;
import com.njydsz.pmis.userinfo.server.service.permission.PermissionService;
import com.njydsz.pmis.userinfo.domain.vo.MenuTreeVO;
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
    public BaseResponse<List<PermissionDO>> list() {
        return BaseResponse.ok(permissionService.listAllEnabled());
    }

    /**
     * 查询当前用户权限编码
     *
     * @param userId 用户 ID（由网关透传）
     * @return 统一响应结果，包含权限编码列表
     */
    @Operation(summary = "查询当前用户权限编码")
    @GetMapping("/mine")
    public BaseResponse<List<String>> mine(@RequestHeader("X-User-Id") String userId) {
        return BaseResponse.ok(permissionService.listPermCodesByUserId(userId));
    }

    /**
     * 查询当前用户菜单树
     *
     * @param userId 用户 ID（由网关透传）
     * @return 统一响应结果，包含菜单树
     */
    @Operation(summary = "查询当前用户菜单树")
    @GetMapping("/menuTree")
    public BaseResponse<List<MenuTreeVO>> menuTree(@RequestHeader("X-User-Id") String userId) {
        return BaseResponse.ok(permissionService.listMenuTreeByUserId(userId));
    }

    /**
     * 查询所有权限并构建为树形结构
     *
     * @return 统一响应结果，包含菜单树
     */
    @Operation(summary = "查询所有权限(构建树)")
    @GetMapping("/tree")
    public BaseResponse<List<MenuTreeVO>> tree() {
        return BaseResponse.ok(permissionService.listAllMenuTree());
    }

    /**
     * 查询角色已分配的权限
     *
     * @param roleId 角色 ID
     * @return 统一响应结果，包含权限列表
     */
    @Operation(summary = "查询角色的权限")
    @GetMapping("/byRole/{roleId}")
    public BaseResponse<List<PermissionDO>> listByRole(@Parameter(description = "角色ID") @PathVariable String roleId) {
        return BaseResponse.ok(permissionService.listByRoleId(roleId));
    }

    /**
     * 查询权限详情
     *
     * @param id 权限 ID
     * @return 统一响应结果，包含权限信息
     */
    @Operation(summary = "权限详情")
    @GetMapping("/{id}")
    public BaseResponse<PermissionDO> get(@Parameter(description = "权限ID") @PathVariable String id) {
        return BaseResponse.ok(permissionService.getById(id));
    }

    /**
     * 创建权限
     *
     * @param dto 权限表单
     * @return 统一响应结果，包含新建权限 ID
     */
    @Operation(summary = "创建权限")
    @AuthApiPermission(apiCodes = "auth:perm:create")
    @OperationLog(module = "权限管理", action = "创建权限", bizType = "PERM")
    @YdszLock(key = "permission:create:#{#dto.permCode}", waitTime = 3, leaseTime = 10, message = "正在创建权限，请稍后")
    @Idempotent(key = "permission:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody PermissionFormDTO dto) {
        return BaseResponse.ok(permissionService.create(dto));
    }

    /**
     * 更新权限
     *
     * @param dto 权限表单
     * @return 统一响应结果
     */
    @Operation(summary = "更新权限")
    @AuthApiPermission(apiCodes = "auth:perm:update")
    @OperationLog(module = "权限管理", action = "更新权限", bizType = "PERM")
    @YdszLock(key = "permission:update:#{#dto.id}", waitTime = 3, leaseTime = 10, message = "正在更新权限，请稍后")
    @Idempotent(key = "permission:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping
    public BaseResponse<Void> update(@Valid @RequestBody PermissionFormDTO dto) {
        permissionService.update(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除权限
     *
     * @param id 权限 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除权限")
    @AuthApiPermission(apiCodes = "auth:perm:delete")
    @OperationLog(module = "权限管理", action = "删除权限", bizType = "PERM")
    @YdszLock(key = "permission:delete:#{#id}", waitTime = 3, leaseTime = 10, message = "正在删除权限，请稍后")
    @Idempotent(key = "permission:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@Parameter(description = "权限ID") @PathVariable String id) {
        permissionService.delete(id);
        return BaseResponse.ok();
    }
}
