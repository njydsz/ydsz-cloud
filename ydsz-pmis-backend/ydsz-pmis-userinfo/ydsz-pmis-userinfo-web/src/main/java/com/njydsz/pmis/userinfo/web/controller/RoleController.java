package com.njydsz.pmis.userinfo.web.controller.permission;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.audit.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.common.lock.annotation.YdszDistributedLock;
import com.njydsz.pmis.common.safe.annotation.RateLimit;
import com.njydsz.pmis.userinfo.domain.dto.permission.RoleFormDTO;
import com.njydsz.pmis.userinfo.domain.dto.permission.RoleQueryDTO;
import com.njydsz.pmis.userinfo.domain.entity.permission.RoleDO;
import com.njydsz.pmis.userinfo.server.service.permission.RoleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 角色接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "角色管理", description = "角色管理相关接口")
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Validated
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
    @AuthApiPermission(apiCodes = "auth:role:list")
    @RateLimit(key = "role:list", qps = 30, windowSeconds = 60)
    @GetMapping
    public BaseResponse<Page<RoleDO>> page(@Valid RoleQueryDTO query) {
        return BaseResponse.ok(roleService.page(query));
    }

    /**
     * 查询所有启用的角色
     *
     * @return 统一响应结果，包含角色列表
     */
    @Operation(summary = "所有启用的角色")
    @RateLimit(key = "role:list", qps = 30, windowSeconds = 60)
    @GetMapping("/all")
    public BaseResponse<List<RoleDO>> listAll() {
        return BaseResponse.ok(roleService.listAllEnabled());
    }

    /**
     * 查询角色详情
     *
     * @param id 角色 ID
     * @return 统一响应结果，包含角色信息
     */
    @Operation(summary = "角色详情")
    @RateLimit(key = "role:list", qps = 30, windowSeconds = 60)
    @GetMapping("/{id}")
    public BaseResponse<RoleDO> get(@Parameter(description = "角色ID") @PathVariable String id) {
        return BaseResponse.ok(roleService.getById(id));
    }

    /**
     * 创建角色
     *
     * @param dto 角色创建参数
     * @return 统一响应结果，包含新建角色 ID
     */
    @Operation(summary = "创建角色")
    @AuthApiPermission(apiCodes = "auth:role:create")
    @OperationLog(module = "权限管理", action = "创建角色", bizType = "ROLE")
    @Idempotent(key = "role:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody RoleFormDTO dto) {
        return BaseResponse.ok(roleService.create(dto));
    }

    /**
     * 更新角色
     *
     * @param dto 角色更新参数
     * @return 统一响应结果
     */
    @Operation(summary = "更新角色")
    @AuthApiPermission(apiCodes = "auth:role:update")
    @OperationLog(module = "权限管理", action = "更新角色", bizType = "ROLE")
    @Idempotent(key = "role:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping
    public BaseResponse<Void> update(@Valid @RequestBody RoleFormDTO dto) {
        roleService.update(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除角色
     *
     * @param id 角色 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除角色")
    @AuthApiPermission(apiCodes = "auth:role:delete")
    @OperationLog(module = "权限管理", action = "删除角色", bizType = "ROLE")
    @Idempotent(key = "role:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@Parameter(description = "角色ID") @PathVariable String id) {
        roleService.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 为角色分配权限
     *
     * @param id            角色 ID
     * @param permissionIds 权限 ID 列表
     * @return 统一响应结果
     */
    @Operation(summary = "为角色分配权限")
    @AuthApiPermission(apiCodes = "auth:role:assign")
    @OperationLog(module = "权限管理", action = "分配权限", bizType = "ROLE")
    @YdszDistributedLock(key = "role:assignPermissions:#{#id}", waitTime = 3, leaseTime = 15, message = "正在分配权限，请稍后")
    @Idempotent(key = "role:assignPermissions", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/permissions")
    public BaseResponse<Void> assignPermissions(@Parameter(description = "角色ID") @PathVariable String id, @Valid @RequestBody List<String> permissionIds) {
        roleService.assignPermissions(id, permissionIds);
        return BaseResponse.ok();
    }

    /**
     * 查询角色的权限 ID 列表
     *
     * @param id 角色 ID
     * @return 统一响应结果，包含权限 ID 列表
     */
    @Operation(summary = "查询角色的权限 ID 列表")
    @RateLimit(key = "role:list", qps = 30, windowSeconds = 60)
    @GetMapping("/{id}/permissions")
    public BaseResponse<List<String>> listPermissions(@Parameter(description = "角色ID") @PathVariable String id) {
        return BaseResponse.ok(roleService.listPermissionIds(id));
    }
}
