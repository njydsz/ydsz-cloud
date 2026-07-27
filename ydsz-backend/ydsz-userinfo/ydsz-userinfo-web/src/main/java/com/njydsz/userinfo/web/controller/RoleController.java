package com.njydsz.userinfo.web.controller;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.userinfo.domain.dto.AssignPermissionsDTO;
import com.njydsz.userinfo.domain.dto.RolePageQueryDTO;
import com.njydsz.userinfo.domain.dto.RoleSaveDTO;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.server.service.RoleService;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 角色 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/role")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色 CRUD、权限分配")
public class RoleController {

    private final RoleService service;

    @GetMapping("/page")
    @Operation(summary = "分页查询角色列表")
    public BaseResponse<PageResponse<List<RoleVO>>> page(@Valid RolePageQueryDTO query) {
        Page<RoleVO> page = service.page(query);
        return BaseResponse.success(PageResponse.success(
                page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords()));
    }

    @GetMapping("/list")
    @Operation(summary = "查询全部角色列表")
    public BaseResponse<List<RoleVO>> list() {
        return BaseResponse.success(service.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询角色")
    public BaseResponse<RoleVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @Audit(module = "角色管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建角色: ' + #dto.roleName")
    @Idempotent(key = "ydsz:userinfo:RoleController:create:lock", ttlSeconds = 5)
    @SentinelRateLimit(resource = "userinfo.role.create", threshold = 50)
    @PostMapping
    @Operation(summary = "创建角色")
    public BaseResponse<String> create(@Valid @RequestBody RoleSaveDTO dto) {
        return BaseResponse.success(service.create(dto));
    }

    @Audit(module = "角色管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新角色: ' + #dto.id")
    @Idempotent(key = "ydsz:userinfo:RoleController:update:lock", ttlSeconds = 5)
    @SentinelRateLimit(resource = "userinfo.role.update", threshold = 50)
    @PutMapping
    @Operation(summary = "更新角色")
    public BaseResponse<Boolean> update(@Valid @RequestBody RoleSaveDTO dto) {
        return BaseResponse.success(service.update(dto));
    }

    @Audit(module = "角色管理", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除角色: ' + #id")
    @SentinelRateLimit(resource = "userinfo.role.remove", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:RoleController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }

    @Audit(module = "角色管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'分配角色权限: ' + #roleId")
    @Idempotent(key = "ydsz:userinfo:RoleController:assignPermissions:lock", ttlSeconds = 5)
    @SentinelRateLimit(resource = "userinfo.role.assignPermissions", threshold = 50)
    @PostMapping("/{roleId}/permissions")
    @Operation(summary = "分配角色权限")
    public BaseResponse<Boolean> assignPermissions(
            @PathVariable String roleId,
            @Valid @RequestBody AssignPermissionsDTO dto) {
        return BaseResponse.success(service.assignPermissions(roleId, dto.getPermissionIds()));
    }

    @GetMapping("/{roleId}/permissions")
    @Operation(summary = "查询角色权限 ID 列表")
    public BaseResponse<List<String>> getRolePermissions(@PathVariable String roleId) {
        return BaseResponse.success(service.getRolePermissionIds(roleId));
    }
}
