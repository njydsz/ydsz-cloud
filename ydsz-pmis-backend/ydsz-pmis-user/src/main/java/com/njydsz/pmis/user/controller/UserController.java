package com.njydsz.pmis.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.user.dto.UserQueryDTO;
import com.njydsz.pmis.user.entity.UserAccountDO;
import com.njydsz.pmis.user.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "权限-用户")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAccountService userAccountService;

    @Operation(summary = "用户分页")
    @PrePermission("auth:user:list")
    @GetMapping
    public R<Page<UserAccountDO>> page(UserQueryDTO query) {
        return R.ok(userAccountService.page(query));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public R<UserAccountDO> get(@PathVariable Long id) {
        return R.ok(userAccountService.findById(id));
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public R<UserAccountDO> me() {
        return R.ok(userAccountService.findById(SecurityContext.getUserId()));
    }

    @Operation(summary = "修改自己的密码")
    @PostMapping("/me/password")
    public R<Void> changeMyPassword(@RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || newPassword == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "原密码或新密码不能为空");
        }
        userAccountService.changePassword(SecurityContext.getUserId(), oldPassword, newPassword);
        return R.ok();
    }

    @Operation(summary = "创建用户")
    @PrePermission("auth:user:create")
    @OperationLog(module = "权限管理", action = "创建用户", bizType = "USER")
    @PostMapping
    public R<Long> create(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        Long employeeId = body.get("employeeId") == null ? null : Long.valueOf(body.get("employeeId").toString());
        if (username == null || password == null) {
            throw new com.njydsz.pmis.common.exception.BizException(
                    com.njydsz.pmis.common.api.BizErrorCode.BAD_REQUEST, "用户名或密码不能为空");
        }
        UserAccountDO u = new UserAccountDO();
        u.setUsername(username);
        u.setEmployeeId(employeeId);
        u.setStatus("ENABLED");
        return R.ok(userAccountService.create(u, password));
    }

    @Operation(summary = "更新用户")
    @PrePermission("auth:user:update")
    @OperationLog(module = "权限管理", action = "更新用户", bizType = "USER")
    @PutMapping
    public R<Void> update(@RequestBody UserAccountDO user) {
        userAccountService.update(user);
        return R.ok();
    }

    @Operation(summary = "删除用户")
    @PrePermission("auth:user:delete")
    @OperationLog(module = "权限管理", action = "删除用户", bizType = "USER")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userAccountService.delete(id);
        return R.ok();
    }

    @Operation(summary = "重置密码")
    @PrePermission("auth:user:reset-password")
    @OperationLog(module = "权限管理", action = "重置密码", bizType = "USER")
    @PostMapping("/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id, @RequestParam @NotBlank String password) {
        userAccountService.resetPassword(id, password);
        return R.ok();
    }

    @Operation(summary = "启用/禁用用户")
    @PrePermission("auth:user:toggle")
    @OperationLog(module = "权限管理", action = "切换状态", bizType = "USER")
    @PostMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Long id, @RequestParam String status) {
        userAccountService.toggleStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "为用户分配角色")
    @PrePermission("auth:user:assign")
    @OperationLog(module = "权限管理", action = "分配角色", bizType = "USER")
    @PutMapping("/{id}/roles")
    public R<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userAccountService.assignRoles(id, roleIds);
        return R.ok();
    }

    @Operation(summary = "查询用户角色 ID 列表")
    @GetMapping("/{id}/roles")
    public R<List<Long>> listRoles(@PathVariable Long id) {
        return R.ok(userAccountService.listRoleIds(id));
    }
}
