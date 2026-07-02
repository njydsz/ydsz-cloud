package com.njydsz.pmis.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.annotation.RequireReAuth;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
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

    /** 用户账号服务 */
    private final UserAccountService userAccountService;

    /**
     * 用户分页查询
     *
     * @param query 查询条件
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "用户分页")
    @PrePermission("auth:user:list")
    @GetMapping
    public Result<Page<UserAccountDO>> page(UserQueryDTO query) {
        return Result.ok(userAccountService.page(query));
    }

    /**
     * 查询用户详情
     *
     * @param id 用户 ID
     * @return 统一响应结果，包含用户信息
     */
    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public Result<UserAccountDO> get(@PathVariable Long id) {
        return Result.ok(userAccountService.findById(id));
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 统一响应结果，包含当前用户信息
     */
    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public Result<UserAccountDO> me() {
        return Result.ok(userAccountService.findById(SecurityContext.getUserId()));
    }

    /**
     * 当前用户修改自己的密码
     *
     * @param body 请求体，包含 oldPassword 与 newPassword
     * @return 统一响应结果
     * @throws BizException 当原密码或新密码为空时抛出
     */
    @Operation(summary = "修改自己的密码")
    @PostMapping("/me/password")
    public Result<Void> changeMyPassword(@RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || newPassword == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "原密码或新密码不能为空");
        }
        userAccountService.changePassword(SecurityContext.getUserId(), oldPassword, newPassword);
        return Result.ok();
    }

    /**
     * 创建用户
     *
     * @param body 请求体，包含 username、password、employeeId
     * @return 统一响应结果，包含新建用户 ID
     * @throws BizException 当用户名或密码为空时抛出
     */
    @Operation(summary = "创建用户")
    @PrePermission("auth:user:create")
    @OperationLog(module = "权限管理", action = "创建用户", bizType = "USER")
    @RateLimit(key = "register", qps = 3, windowSeconds = 60,
            message = "用户创建过于频繁，请 60 秒后再试")
    @PostMapping
    public Result<Long> create(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        Long employeeId = body.get("employeeId") == null ? null : Long.valueOf(body.get("employeeId").toString());
        if (username == null || password == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "用户名或密码不能为空");
        }
        UserAccountDO u = new UserAccountDO();
        u.setUsername(username);
        u.setEmployeeId(employeeId);
        u.setStatus("ENABLED");
        return Result.ok(userAccountService.create(u, password));
    }

    /**
     * 更新用户信息
     *
     * @param user 用户实体
     * @return 统一响应结果
     */
    @Operation(summary = "更新用户")
    @PrePermission("auth:user:update")
    @OperationLog(module = "权限管理", action = "更新用户", bizType = "USER")
    @PutMapping
    public Result<Void> update(@RequestBody UserAccountDO user) {
        userAccountService.update(user);
        return Result.ok();
    }

    /**
     * 删除用户
     *
     * @param id 用户 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除用户")
    @PrePermission("auth:user:delete")
    @RequireReAuth(code = "USER_DELETE", name = "删除用户")
    @OperationLog(module = "权限管理", action = "删除用户", bizType = "USER")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userAccountService.delete(id);
        return Result.ok();
    }

    /**
     * 重置用户密码
     *
     * @param id       用户 ID
     * @param password 新密码
     * @return 统一响应结果
     */
    @Operation(summary = "重置密码")
    @PrePermission("auth:user:reset-password")
    @RequireReAuth(code = "USER_RESET_PASSWORD", name = "重置用户密码")
    @OperationLog(module = "权限管理", action = "重置密码", bizType = "USER")
    @RateLimit(key = "register", qps = 3, windowSeconds = 60,
            message = "密码重置过于频繁，请 60 秒后再试")
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestParam @NotBlank String password) {
        userAccountService.resetPassword(id, password);
        return Result.ok();
    }

    /**
     * 启用/禁用用户
     *
     * @param id     用户 ID
     * @param status 目标状态（ENABLED/DISABLED）
     * @return 统一响应结果
     */
    @Operation(summary = "启用/禁用用户")
    @PrePermission("auth:user:toggle")
    @OperationLog(module = "权限管理", action = "切换状态", bizType = "USER")
    @PostMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam String status) {
        userAccountService.toggleStatus(id, status);
        return Result.ok();
    }

    /**
     * 为用户分配角色
     *
     * @param id      用户 ID
     * @param roleIds 角色 ID 列表
     * @return 统一响应结果
     */
    @Operation(summary = "为用户分配角色")
    @PrePermission("auth:user:assign")
    @OperationLog(module = "权限管理", action = "分配角色", bizType = "USER")
    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userAccountService.assignRoles(id, roleIds);
        return Result.ok();
    }

    /**
     * 查询用户已分配的角色 ID 列表
     *
     * @param id 用户 ID
     * @return 统一响应结果，包含角色 ID 列表
     */
    @Operation(summary = "查询用户角色 ID 列表")
    @GetMapping("/{id}/roles")
    public Result<List<Long>> listRoles(@PathVariable Long id) {
        return Result.ok(userAccountService.listRoleIds(id));
    }
}
